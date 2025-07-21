package ec.edu.utn.com.example.remotemonitoringapp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import android.os.Environment;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;

public class DeviceInfoHelper {

    private static final String ETIQUETA = "AyudanteInfoDispositivo";
    private Context contexto;

    public DeviceInfoHelper(Context contexto) {
        this.contexto = contexto;
    }

    public JSONObject obtenerEstadoDispositivoJson() throws JSONException {
        JSONObject estadoDispositivo = new JSONObject();

        // Información de la batería
        JSONObject infoBateria = obtenerInfoBateria();
        estadoDispositivo.put("bateria", infoBateria);

        // Información de la red
        JSONObject infoRed = obtenerInfoRed();
        estadoDispositivo.put("red", infoRed);

        // Información de almacenamiento
        JSONObject infoAlmacenamiento = obtenerInfoAlmacenamiento();
        estadoDispositivo.put("almacenamiento", infoAlmacenamiento);

        // Información del sistema
        JSONObject infoSistema = obtenerInfoSistema();
        estadoDispositivo.put("sistema", infoSistema);

        // Información del hardware
        JSONObject infoHardware = obtenerInfoHardware();
        estadoDispositivo.put("hardware", infoHardware);

        return estadoDispositivo;
    }

    private JSONObject obtenerInfoBateria() throws JSONException {
        JSONObject infoBateria = new JSONObject();

        try {
            IntentFilter filtro = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent estadoBateria = contexto.registerReceiver(null, filtro);

            if (estadoBateria != null) {
                int nivel = estadoBateria.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int escala = estadoBateria.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int estado = estadoBateria.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int conectado = estadoBateria.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                int voltaje = estadoBateria.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int temperatura = estadoBateria.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

                float porcentajeBateria = (nivel * 100.0f) / escala;

                infoBateria.put("porcentaje_nivel", Math.round(porcentajeBateria));
                infoBateria.put("nivel_crudo", nivel);
                infoBateria.put("escala", escala);
                infoBateria.put("estado", obtenerCadenaEstadoBateria(estado));
                infoBateria.put("conectado", obtenerCadenaConectadoBateria(conectado));
                infoBateria.put("voltaje_mv", voltaje);
                infoBateria.put("temperatura_celsius", temperatura / 10.0f);
                infoBateria.put("esta_cargando", estado == BatteryManager.BATTERY_STATUS_CHARGING);
                infoBateria.put("es_bajo", porcentajeBateria < 20);
            }
        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener información de la batería", e);
            infoBateria.put("error", "No se pudo obtener información de la batería");
        }

        return infoBateria;
    }

    private JSONObject obtenerInfoRed() throws JSONException {
        JSONObject infoRed = new JSONObject();

        try {
            ConnectivityManager gestorConectividad = (ConnectivityManager)
                    contexto.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (gestorConectividad != null) {
                NetworkInfo redActiva = gestorConectividad.getActiveNetworkInfo();

                if (redActiva != null) {
                    infoRed.put("esta_conectado", redActiva.isConnected());
                    infoRed.put("tipo", redActiva.getTypeName());
                    infoRed.put("subtipo", redActiva.getSubtypeName());
                    infoRed.put("estado", redActiva.getState().toString());
                    infoRed.put("razon", redActiva.getReason());
                    infoRed.put("info_extra", redActiva.getExtraInfo());
                } else {
                    infoRed.put("esta_conectado", false);
                    infoRed.put("tipo", "ninguno");
                }

                // Verificar tipos de red específicos
                NetworkInfo wifi = gestorConectividad.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                NetworkInfo movil = gestorConectividad.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

                infoRed.put("wifi_disponible", wifi != null && wifi.isConnected());
                infoRed.put("movil_disponible", movil != null && movil.isConnected());
            }
        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener información de la red", e);
            infoRed.put("error", "No se pudo obtener información de la red");
        }

        return infoRed;
    }

    private JSONObject obtenerInfoAlmacenamiento() throws JSONException {
        JSONObject infoAlmacenamiento = new JSONObject();

        try {
            // Almacenamiento interno
            File directorioInterno = Environment.getDataDirectory();
            StatFs estadisticasInternas = new StatFs(directorioInterno.getPath());

            long bytesDisponiblesInternos = estadisticasInternas.getAvailableBytes();
            long bytesTotalesInternos = estadisticasInternas.getTotalBytes();
            long bytesUsadosInternos = bytesTotalesInternos - bytesDisponiblesInternos;

            JSONObject almacenamientoInterno = new JSONObject();
            almacenamientoInterno.put("bytes_totales", bytesTotalesInternos);
            almacenamientoInterno.put("bytes_disponibles", bytesDisponiblesInternos);
            almacenamientoInterno.put("bytes_usados", bytesUsadosInternos);
            almacenamientoInterno.put("gb_totales", bytesAGB(bytesTotalesInternos));
            almacenamientoInterno.put("gb_disponibles", bytesAGB(bytesDisponiblesInternos));
            almacenamientoInterno.put("gb_usados", bytesAGB(bytesUsadosInternos));
            almacenamientoInterno.put("porcentaje_uso", Math.round((bytesUsadosInternos * 100.0f) / bytesTotalesInternos));

            infoAlmacenamiento.put("interno", almacenamientoInterno);

            // Almacenamiento externo (si está disponible)
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File directorioExterno = Environment.getExternalStorageDirectory();
                StatFs estadisticasExternas = new StatFs(directorioExterno.getPath());

                long bytesDisponiblesExternos = estadisticasExternas.getAvailableBytes();
                long bytesTotalesExternos = estadisticasExternas.getTotalBytes();
                long bytesUsadosExternos = bytesTotalesExternos - bytesDisponiblesExternos;

                JSONObject almacenamientoExterno = new JSONObject();
                almacenamientoExterno.put("bytes_totales", bytesTotalesExternos);
                almacenamientoExterno.put("bytes_disponibles", bytesDisponiblesExternos);
                almacenamientoExterno.put("bytes_usados", bytesUsadosExternos);
                almacenamientoExterno.put("gb_totales", bytesAGB(bytesTotalesExternos));
                almacenamientoExterno.put("gb_disponibles", bytesAGB(bytesDisponiblesExternos));
                almacenamientoExterno.put("gb_usados", bytesAGB(bytesUsadosExternos));
                almacenamientoExterno.put("porcentaje_uso", Math.round((bytesUsadosExternos * 100.0f) / bytesTotalesExternos));

                infoAlmacenamiento.put("externo", almacenamientoExterno);
            }

        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener información de almacenamiento", e);
            infoAlmacenamiento.put("error", "No se pudo obtener información de almacenamiento");
        }

        return infoAlmacenamiento;
    }

    private JSONObject obtenerInfoSistema() throws JSONException {
        JSONObject infoSistema = new JSONObject();

        try {
            infoSistema.put("version_android", Build.VERSION.RELEASE);
            infoSistema.put("nivel_api", Build.VERSION.SDK_INT);
            infoSistema.put("id_construccion", Build.ID);
            infoSistema.put("parche_seguridad", Build.VERSION.SECURITY_PATCH);
            infoSistema.put("cargador_arranque", Build.BOOTLOADER);
            infoSistema.put("huella_digital", Build.FINGERPRINT);
            infoSistema.put("incremental", Build.VERSION.INCREMENTAL);
            infoSistema.put("tiempo_actividad_milis", System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime());

            // Información de tiempo de ejecución
            Runtime tiempoEjecucion = Runtime.getRuntime();
            infoSistema.put("procesadores_disponibles", tiempoEjecucion.availableProcessors());
            infoSistema.put("memoria_maxima_mb", tiempoEjecucion.maxMemory() / (1024 * 1024));
            infoSistema.put("memoria_total_mb", tiempoEjecucion.totalMemory() / (1024 * 1024));
            infoSistema.put("memoria_libre_mb", tiempoEjecucion.freeMemory() / (1024 * 1024));
            infoSistema.put("memoria_usada_mb", (tiempoEjecucion.totalMemory() - tiempoEjecucion.freeMemory()) / (1024 * 1024));

        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener información del sistema", e);
            infoSistema.put("error", "No se pudo obtener información del sistema");
        }

        return infoSistema;
    }

    private JSONObject obtenerInfoHardware() throws JSONException {
        JSONObject infoHardware = new JSONObject();

        try {
            infoHardware.put("fabricante", Build.MANUFACTURER);
            infoHardware.put("modelo", Build.MODEL);
            infoHardware.put("marca", Build.BRAND);
            infoHardware.put("dispositivo", Build.DEVICE);
            infoHardware.put("producto", Build.PRODUCT);
            infoHardware.put("hardware", Build.HARDWARE);
            infoHardware.put("placa", Build.BOARD);
            infoHardware.put("cpu_abi", Build.CPU_ABI);
            infoHardware.put("cpu_abi2", Build.CPU_ABI2);

            // Información de la pantalla
            android.util.DisplayMetrics metricas = contexto.getResources().getDisplayMetrics();
            JSONObject infoPantalla = new JSONObject();
            infoPantalla.put("densidad_dpi", metricas.densityDpi);
            infoPantalla.put("densidad", metricas.density);
            infoPantalla.put("ancho_pixeles", metricas.widthPixels);
            infoPantalla.put("alto_pixeles", metricas.heightPixels);
            infoPantalla.put("xdpi", metricas.xdpi);
            infoPantalla.put("ydpi", metricas.ydpi);

            infoHardware.put("pantalla", infoPantalla);

        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener información del hardware", e);
            infoHardware.put("error", "No se pudo obtener información del hardware");
        }

        return infoHardware;
    }

    // Métodos auxiliares
    private String obtenerCadenaEstadoBateria(int estado) {
        switch (estado) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "cargando";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "descargando";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "completa";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "sin_cargar";
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                return "desconocido";
        }
    }

    private String obtenerCadenaConectadoBateria(int conectado) {
        switch (conectado) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "ca";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "usb";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "inalambrico";
            default:
                return "desconectado";
        }
    }

    private String bytesAGB(long bytes) {
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // Métodos públicos para acceso rápido
    public String obtenerModeloDispositivo() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    public String obtenerVersionSO() {
        return Build.VERSION.RELEASE;
    }

    public int obtenerNivelBateria() {
        try {
            IntentFilter filtro = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent estadoBateria = contexto.registerReceiver(null, filtro);

            if (estadoBateria != null) {
                int nivel = estadoBateria.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int escala = estadoBateria.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                return Math.round((nivel * 100.0f) / escala);
            }
        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al obtener nivel de batería", e);
        }
        return -1;
    }

    public boolean estaConectadoAInternet() {
        try {
            ConnectivityManager gestorConectividad = (ConnectivityManager)
                    contexto.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (gestorConectividad != null) {
                NetworkInfo redActiva = gestorConectividad.getActiveNetworkInfo();
                return redActiva != null && redActiva.isConnected();
            }
        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al verificar conectividad a Internet", e);
        }
        return false;
    }
}