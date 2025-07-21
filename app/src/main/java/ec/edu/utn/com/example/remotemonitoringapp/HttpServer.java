package ec.edu.utn.com.example.remotemonitoringapp;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {

    private static final String ETIQUETA = "ServidorHttp";
    private static final int PUERTO = 8080;
    private static final String HTTP_OK = "HTTP/1.1 200 OK\r\n";
    private static final String HTTP_NO_AUTORIZADO = "HTTP/1.1 401 Unauthorized\r\n";
    private static final String HTTP_SOLICITUD_INCORRECTA = "HTTP/1.1 400 Bad Request\r\n";
    private static final String HTTP_NO_ENCONTRADO = "HTTP/1.1 404 Not Found\r\n";
    private static final String HTTP_ERROR_INTERNO = "HTTP/1.1 500 Internal Server Error\r\n";

    private Context contexto;
    private DatabaseHelper ayudanteBaseDatos;
    private DeviceInfoHelper ayudanteInfoDispositivo;
    private ServerSocket socketServidor;
    private ExecutorService ejecutor;
    private boolean estaEjecutando = false;

    public HttpServer(Context contexto, DatabaseHelper ayudanteBaseDatos) {
        this.contexto = contexto;
        this.ayudanteBaseDatos = ayudanteBaseDatos;
        this.ayudanteInfoDispositivo = new DeviceInfoHelper(contexto);
        this.ejecutor = Executors.newFixedThreadPool(10);
    }

    public void iniciar() throws IOException {
        if (estaEjecutando) {
            Log.w(ETIQUETA, "El servidor ya está en ejecución");
            return;
        }

        socketServidor = new ServerSocket(PUERTO);
        estaEjecutando = true;

        ejecutor.submit(() -> {
            Log.d(ETIQUETA, "Servidor HTTP iniciado en el puerto " + PUERTO);

            while (estaEjecutando && !socketServidor.isClosed()) {
                try {
                    Socket socketCliente = socketServidor.accept();
                    ejecutor.submit(() -> manejarSolicitud(socketCliente));
                } catch (IOException e) {
                    if (estaEjecutando) {
                        Log.e(ETIQUETA, "Error al aceptar conexión del cliente", e);
                    }
                }
            }
        });
    }

    public void detener() {
        estaEjecutando = false;

        if (socketServidor != null && !socketServidor.isClosed()) {
            try {
                socketServidor.close();
            } catch (IOException e) {
                Log.e(ETIQUETA, "Error al cerrar el socket del servidor", e);
            }
        }

        if (ejecutor != null) {
            ejecutor.shutdown();
        }

        Log.d(ETIQUETA, "Servidor HTTP detenido");
    }

    private void manejarSolicitud(Socket socketCliente) {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
             OutputStreamWriter escritor = new OutputStreamWriter(socketCliente.getOutputStream())) {

            String lineaSolicitud = lector.readLine();
            if (lineaSolicitud == null) {
                return;
            }

            Log.d(ETIQUETA, "Solicitud: " + lineaSolicitud);

            // Analizar línea de solicitud
            String[] partesSolicitud = lineaSolicitud.split(" ");
            if (partesSolicitud.length < 3) {
                enviarRespuesta(escritor, HTTP_SOLICITUD_INCORRECTA, "text/plain", "Formato de solicitud inválido");
                return;
            }

            String metodo = partesSolicitud[0];
            String ruta = partesSolicitud[1];

            // Leer cabeceras
            Map<String, String> cabeceras = new HashMap<>();
            String lineaCabecera;
            while ((lineaCabecera = lector.readLine()) != null && !lineaCabecera.isEmpty()) {
                String[] partesCabecera = lineaCabecera.split(": ", 2);
                if (partesCabecera.length == 2) {
                    cabeceras.put(partesCabecera[0].toLowerCase(), partesCabecera[1]);
                }
            }

            // Manejar autenticación
            if (!estaAutenticado(cabeceras)) {
                enviarRespuesta(escritor, HTTP_NO_AUTORIZADO, "application/json",
                        "{\"error\": \"Se requiere autenticación\", \"mensaje\": \"Por favor proporciona credenciales válidas\"}");
                return;
            }

            // Enrutar la solicitud
            enrutarSolicitud(metodo, ruta, cabeceras, escritor);

        } catch (IOException e) {
            Log.e(ETIQUETA, "Error al manejar la solicitud", e);
        } finally {
            try {
                socketCliente.close();
            } catch (IOException e) {
                Log.e(ETIQUETA, "Error al cerrar el socket del cliente", e);
            }
        }
    }

    private boolean estaAutenticado(Map<String, String> cabeceras) {
        String cabeceraAutorizacion = cabeceras.get("authorization");
        if (cabeceraAutorizacion == null) {
            return false;
        }

        // Verificar token Bearer
        if (cabeceraAutorizacion.startsWith("Bearer ")) {
            String token = cabeceraAutorizacion.substring(7);
            return ayudanteBaseDatos.validarToken(token);
        }

        // Verificar autenticación Basic
        if (cabeceraAutorizacion.startsWith("Basic ")) {
            String credencialesCodificadas = cabeceraAutorizacion.substring(6);
            String credencialesDecodificadas = new String(Base64.decode(credencialesCodificadas, Base64.DEFAULT));
            String[] credenciales = credencialesDecodificadas.split(":", 2);

            if (credenciales.length == 2) {
                return ayudanteBaseDatos.validarCredenciales(credenciales[0], credenciales[1]);
            }
        }

        return false;
    }

    private void enrutarSolicitud(String metodo, String ruta, Map<String, String> cabeceras, OutputStreamWriter escritor) throws IOException {
        if (!"GET".equals(metodo)) {
            enviarRespuesta(escritor, HTTP_SOLICITUD_INCORRECTA, "application/json",
                    "{\"error\": \"Método no permitido\", \"mensaje\": \"Solo se soporta el método GET\"}");
            return;
        }

        // Analizar ruta y parámetros de consulta
        String[] partesRuta = ruta.split("\\?", 2);
        String rutaReal = partesRuta[0];
        Map<String, String> parametrosConsulta = new HashMap<>();

        if (partesRuta.length > 1) {
            analizarParametrosConsulta(partesRuta[1], parametrosConsulta);
        }

        switch (rutaReal) {
            case "/api/sensor_data":
                manejarSolicitudDatosSensor(parametrosConsulta, escritor);
                break;
            case "/api/device_status":
                manejarSolicitudEstadoDispositivo(escritor);
                break;
            case "/":
            case "/api":
                manejarSolicitudRaiz(escritor);
                break;
            default:
                enviarRespuesta(escritor, HTTP_NO_ENCONTRADO, "application/json",
                        "{\"error\": \"No encontrado\", \"mensaje\": \"Punto final no encontrado\"}");
        }
    }

    private void analizarParametrosConsulta(String cadenaConsulta, Map<String, String> parametrosConsulta) {
        String[] pares = cadenaConsulta.split("&");
        for (String par : pares) {
            String[] claveValor = par.split("=", 2);
            if (claveValor.length == 2) {
                try {
                    String clave = URLDecoder.decode(claveValor[0], "UTF-8");
                    String valor = URLDecoder.decode(claveValor[1], "UTF-8");
                    parametrosConsulta.put(clave, valor);
                } catch (Exception e) {
                    Log.e(ETIQUETA, "Error al analizar parámetro de consulta: " + par, e);
                }
            }
        }
    }

    private void manejarSolicitudDatosSensor(Map<String, String> parametrosConsulta, OutputStreamWriter escritor) throws IOException {
        try {
            JSONObject respuesta = new JSONObject();
            JSONArray datosSensor;

            String tiempoInicioStr = parametrosConsulta.get("tiempo_inicio");
            String tiempoFinStr = parametrosConsulta.get("tiempo_fin");

            if (tiempoInicioStr != null && tiempoFinStr != null) {
                // Obtener datos para un rango de tiempo específico
                long tiempoInicio = Long.parseLong(tiempoInicioStr);
                long tiempoFin = Long.parseLong(tiempoFinStr);
                datosSensor = ayudanteBaseDatos.obtenerDatosSensorPorRangoTiempo(tiempoInicio, tiempoFin);

                respuesta.put("tipo_consulta", "rango_tiempo");
                respuesta.put("tiempo_inicio", tiempoInicio);
                respuesta.put("tiempo_fin", tiempoFin);
            } else {
                // Obtener todos los datos recientes (últimos 100 registros)
                datosSensor = ayudanteBaseDatos.obtenerTodosDatosSensor();
                respuesta.put("tipo_consulta", "reciente");
            }

            respuesta.put("estado", "éxito");
            respuesta.put("conteo", datosSensor.length());
            respuesta.put("datos", datosSensor);
            respuesta.put("marca_tiempo", System.currentTimeMillis());

            enviarRespuesta(escritor, HTTP_OK, "application/json", respuesta.toString());

        } catch (NumberFormatException e) {
            enviarRespuesta(escritor, HTTP_SOLICITUD_INCORRECTA, "application/json",
                    "{\"error\": \"Formato de tiempo inválido\", \"mensaje\": \"tiempo_inicio y tiempo_fin deben ser marcas de tiempo válidas\"}");
        } catch (JSONException e) {
            Log.e(ETIQUETA, "Error al crear respuesta de datos del sensor", e);
            enviarRespuesta(escritor, HTTP_ERROR_INTERNO, "application/json",
                    "{\"error\": \"Error interno del servidor\", \"mensaje\": \"Error al procesar datos del sensor\"}");
        }
    }

    private void manejarSolicitudEstadoDispositivo(OutputStreamWriter escritor) throws IOException {
        try {
            JSONObject respuesta = new JSONObject();

            respuesta.put("estado", "éxito");
            respuesta.put("info_dispositivo", ayudanteInfoDispositivo.obtenerEstadoDispositivoJson());
            respuesta.put("recoleccion_datos", obtenerEstadoRecoleccionDatos());
            respuesta.put("marca_tiempo", System.currentTimeMillis());

            enviarRespuesta(escritor, HTTP_OK, "application/json", respuesta.toString());

        } catch (JSONException e) {
            Log.e(ETIQUETA, "Error al crear respuesta de estado del dispositivo", e);
            enviarRespuesta(escritor, HTTP_ERROR_INTERNO, "application/json",
                    "{\"error\": \"Error interno del servidor\", \"mensaje\": \"Error al obtener estado del dispositivo\"}");
        }
    }

    private JSONObject obtenerEstadoRecoleccionDatos() throws JSONException {
        JSONObject estadoDatos = new JSONObject();

        int totalRegistros = ayudanteBaseDatos.obtenerConteoDatosSensor();
        estadoDatos.put("total_registros", totalRegistros);
        estadoDatos.put("estado_servidor", "en_ejecución");
        estadoDatos.put("intervalo_recoleccion", "30_segundos");

        return estadoDatos;
    }

    private void manejarSolicitudRaiz(OutputStreamWriter escritor) throws IOException {
        try {
            JSONObject respuesta = new JSONObject();

            respuesta.put("servicio", "API de Monitoreo Remoto");
            respuesta.put("versión", "1.0");
            respuesta.put("estado", "en_ejecución");
            respuesta.put("puntos_finales", new JSONArray()
                    .put("/api/sensor_data")
                    .put("/api/device_status"));

            JSONObject autenticacion = new JSONObject();
            autenticacion.put("tipo", "Token Bearer o Autenticación Básica");
            autenticacion.put("cabecera", "Authorization");
            autenticacion.put("ejemplo_bearer", "Bearer token_por_defecto_123456789");
            autenticacion.put("ejemplo_basico", "Basic YWRtaW46YWRtaW4xMjM=");

            respuesta.put("autenticacion", autenticacion);
            respuesta.put("marca_tiempo", System.currentTimeMillis());

            enviarRespuesta(escritor, HTTP_OK, "application/json", respuesta.toString());

        } catch (JSONException e) {
            Log.e(ETIQUETA, "Error al crear respuesta raíz", e);
            enviarRespuesta(escritor, HTTP_ERROR_INTERNO, "application/json",
                    "{\"error\": \"Error interno del servidor\"}");
        }
    }

    private void enviarRespuesta(OutputStreamWriter escritor, String estado, String tipoContenido, String cuerpo) throws IOException {
        StringBuilder respuesta = new StringBuilder();
        respuesta.append(estado);
        respuesta.append("Content-Type: ").append(tipoContenido).append("\r\n");
        respuesta.append("Content-Length: ").append(cuerpo.getBytes().length).append("\r\n");
        respuesta.append("Access-Control-Allow-Origin: *\r\n");
        respuesta.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
        respuesta.append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n");
        respuesta.append("\r\n");
        respuesta.append(cuerpo);

        escritor.write(respuesta.toString());
        escritor.flush();

        Log.d(ETIQUETA, "Respuesta enviada: " + estado.trim());
    }

    public boolean estaEjecutando() {
        return estaEjecutando;
    }

    public int obtenerPuerto() {
        return PUERTO;
    }
}