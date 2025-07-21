package ec.edu.utn.com.example.remotemonitoringapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String ETIQUETA = "ActividadPrincipal";
    private static final int CODIGO_SOLICITUD_PERMISO_UBICACION = 1001;
    private static final long INTERVALO_ACTUALIZACION_UBICACION = 30000; // 30 segundos
    private static final String PREFS_NAME = "MonitoringPrefs";
    private static final String KEY_DAYS = "collection_days";
    private static final String KEY_START_HOUR = "start_hour";
    private static final String KEY_END_HOUR = "end_hour";

    private LocationManager gestorUbicacion;
    private DatabaseHelper ayudanteBaseDatos;
    private HttpServer servidorHttp;
    private Handler manejadorUbicacion;
    private Runnable tareaUbicacion;

    // Componentes de la interfaz de usuario
    private Switch interruptorRecoleccionDatos;
    private Button btnIniciarServidor, btnDetenerServidor, btnConfigurar;
    private TextView tvEstado, tvUltimaUbicacion, tvInfoServidor, tvInfoDispositivo;

    private boolean estaRecolectando = false;
    private boolean servidorEnEjecucion = false;
    private boolean[] diasProgramacion = new boolean[7]; // Domingo a Sábado
    private int horaInicio = 0, horaFin = 23;

    private ActivityResultLauncher<Intent> settingsLauncher;

    @Override
    protected void onCreate(Bundle estadoGuardado) {
        super.onCreate(estadoGuardado);
        setContentView(R.layout.activity_main);

        inicializarComponentes();
        configurarInterfazUsuario();
        solicitarPermisoUbicacion();
        cargarPreferencias();
        // Registrar el lanzador para la actividad de configuración
        settingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        cargarPreferencias();
                        if (estaRecolectando) {
                            detenerRecoleccionDatos();
                            iniciarRecoleccionDatos();
                        }
                    }
                });
    }

    /**
     * Método para obtener el tiempo actual en la zona horaria de Ecuador
     */
    private long obtenerTiempoEcuador() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("America/Guayaquil"));
        return calendar.getTimeInMillis();
    }

    /**
     * Método para formatear fechas con zona horaria de Ecuador
     */
    private String formatearFechaEcuador(long timestamp, String patron) {
        SimpleDateFormat sdf = new SimpleDateFormat(patron, Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("America/Guayaquil"));
        return sdf.format(new Date(timestamp));
    }

    private void inicializarComponentes() {
        gestorUbicacion = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        ayudanteBaseDatos = new DatabaseHelper(this);
        servidorHttp = new HttpServer(this, ayudanteBaseDatos);
        manejadorUbicacion = new Handler(Looper.getMainLooper());

        // Inicializar componentes de la interfaz de usuario
        interruptorRecoleccionDatos = findViewById(R.id.switch_data_collection);
        btnIniciarServidor = findViewById(R.id.btn_start_server);
        btnDetenerServidor = findViewById(R.id.btn_stop_server);
        btnConfigurar = findViewById(R.id.btn_configure);
        tvEstado = findViewById(R.id.tv_status);
        tvUltimaUbicacion = findViewById(R.id.tv_last_location);
        tvInfoServidor = findViewById(R.id.tv_server_info);
        tvInfoDispositivo = findViewById(R.id.tv_device_info);
    }

    private void configurarInterfazUsuario() {
        interruptorRecoleccionDatos.setOnCheckedChangeListener((vistaBoton, estaMarcado) -> {
            if (estaMarcado) {
                iniciarRecoleccionDatos();
            } else {
                detenerRecoleccionDatos();
            }
        });

        btnIniciarServidor.setOnClickListener(v -> iniciarServidorHttp());
        btnDetenerServidor.setOnClickListener(v -> detenerServidorHttp());
        btnConfigurar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            settingsLauncher.launch(intent);
        });

        actualizarInfoDispositivo();
        actualizarInfoServidor();
    }

    private void cargarPreferencias() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String daysString = prefs.getString(KEY_DAYS, "1111111"); // Por defecto todos los días
        for (int i = 0; i < diasProgramacion.length && i < daysString.length(); i++) {
            diasProgramacion[i] = daysString.charAt(i) == '1';
        }
        horaInicio = prefs.getInt(KEY_START_HOUR, 0);
        horaFin = prefs.getInt(KEY_END_HOUR, 23);
        Log.d(ETIQUETA, "Preferencias cargadas: días=" + daysString + ", horaInicio=" + horaInicio + ", horaFin=" + horaFin);
    }

    private void solicitarPermisoUbicacion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    CODIGO_SOLICITUD_PERMISO_UBICACION);
        } else {
            verificarServiciosUbicacion();
        }
    }

    @Override
    public void onRequestPermissionsResult(int codigoSolicitud, String[] permisos, int[] resultados) {
        super.onRequestPermissionsResult(codigoSolicitud, permisos, resultados);
        if (codigoSolicitud == CODIGO_SOLICITUD_PERMISO_UBICACION) {
            if (resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de ubicación concedido", Toast.LENGTH_SHORT).show();
                verificarServiciosUbicacion();
            } else {
                Toast.makeText(this, "Se requiere permiso de ubicación", Toast.LENGTH_LONG).show();
                interruptorRecoleccionDatos.setChecked(false);
            }
        }
    }

    private void verificarServiciosUbicacion() {
        if (!gestorUbicacion.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !gestorUbicacion.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "Por favor habilita los servicios de ubicación", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception e) {
                Log.e(ETIQUETA, "Error al abrir ajustes de ubicación: " + e.getMessage());
                Toast.makeText(this, "No se pudo abrir los ajustes de ubicación. Habilítalos manualmente.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void iniciarRecoleccionDatos() {
        if (!tienePermisoUbicacion()) {
            solicitarPermisoUbicacion();
            interruptorRecoleccionDatos.setChecked(false);
            return;
        }

        if (!gestorUbicacion.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !gestorUbicacion.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            actualizarEstado("Servicios de ubicación desactivados");
            interruptorRecoleccionDatos.setChecked(false);
            return;
        }

        estaRecolectando = true;
        actualizarEstado("Recolección de datos iniciada");

        tareaUbicacion = new Runnable() {
            @Override
            public void run() {
                if (estaRecolectando) {
                    // Usar Calendar con zona horaria de Ecuador para verificar horarios
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"));
                    int dia = cal.get(Calendar.DAY_OF_WEEK) - 1;
                    int hora = cal.get(Calendar.HOUR_OF_DAY);
                    if (diasProgramacion[dia] && hora >= horaInicio && hora <= horaFin) {
                        solicitarActualizacionUbicacion();
                    } else {
                        actualizarEstado("Fuera del horario programado");
                    }
                    manejadorUbicacion.postDelayed(this, INTERVALO_ACTUALIZACION_UBICACION);
                }
            }
        };

        manejadorUbicacion.post(tareaUbicacion);
        Log.d(ETIQUETA, "Recolección de datos GPS iniciada");
    }

    private void detenerRecoleccionDatos() {
        estaRecolectando = false;
        if (tareaUbicacion != null) {
            manejadorUbicacion.removeCallbacks(tareaUbicacion);
        }
        actualizarEstado("Recolección de datos detenida");
        Log.d(ETIQUETA, "Recolección de datos GPS detenida");
    }

    private void solicitarActualizacionUbicacion() {
        if (!tienePermisoUbicacion()) {
            actualizarEstado("Permiso denegado");
            interruptorRecoleccionDatos.setChecked(false);
            return;
        }

        try {
            if (gestorUbicacion.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gestorUbicacion.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper());
            }
            if (gestorUbicacion.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                gestorUbicacion.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            Log.e(ETIQUETA, "Excepción de seguridad: " + e.getMessage());
            actualizarEstado("Error: Acceso a ubicación denegado");
            interruptorRecoleccionDatos.setChecked(false);
        } catch (IllegalArgumentException e) {
            Log.e(ETIQUETA, "Proveedor no disponible: " + e.getMessage());
            actualizarEstado("Error: Proveedor de ubicación no disponible");
        }
    }

    @Override
    public void onLocationChanged(Location ubicacion) {
        if (ubicacion != null && estaRecolectando) {
            String idDispositivo = obtenerIdUnicoDispositivo();
            // Usar el tiempo de Ecuador para mostrar en pantalla
            long marcaTiempoEcuador = obtenerTiempoEcuador();

            // Guardar en base de datos (ya usa tiempo de Ecuador internamente)
            ayudanteBaseDatos.insertarDatosSensorConTiempoEcuador(ubicacion.getLatitude(), ubicacion.getLongitude(), idDispositivo);

            // Mostrar en pantalla con tiempo de Ecuador
            actualizarPantallaUltimaUbicacion(ubicacion, marcaTiempoEcuador);
            // Log.d(ETIQUETA, "Ubicación actualizada: " + ubicacion.getLatitude() + ", " + ubicacion.getLongitude());
        }
    }

    private void actualizarPantallaUltimaUbicacion(Location ubicacion, long marcaTiempo) {
        // Usar el método que formatea con zona horaria de Ecuador
        String horaFormateada = formatearFechaEcuador(marcaTiempo, "yyyy-MM-dd HH:mm:ss");
        tvUltimaUbicacion.setText(String.format("Lat: %.6f\nLng: %.6f\nHora: %s",
                ubicacion.getLatitude(), ubicacion.getLongitude(), horaFormateada));
    }

    private void iniciarServidorHttp() {
        if (servidorEnEjecucion) {
            Toast.makeText(this, "El servidor ya está en ejecución", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            servidorHttp.iniciar();
            servidorEnEjecucion = true;
            actualizarEstado("Servidor HTTP iniciado");
            actualizarInfoServidor();
            btnIniciarServidor.setEnabled(false);
            btnDetenerServidor.setEnabled(true);
            Log.d(ETIQUETA, "Servidor HTTP iniciado");
        } catch (Exception e) {
            Log.e(ETIQUETA, "Error al iniciar el servidor", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void detenerServidorHttp() {
        if (!servidorEnEjecucion) {
            Toast.makeText(this, "El servidor no está en ejecución", Toast.LENGTH_SHORT).show();
            return;
        }
        servidorHttp.detener();
        servidorEnEjecucion = false;
        actualizarEstado("Servidor HTTP detenido");
        actualizarInfoServidor();
        btnIniciarServidor.setEnabled(true);
        btnDetenerServidor.setEnabled(false);
        Log.d(ETIQUETA, "Servidor HTTP detenido");
    }

    private void actualizarEstado(String estado) {
        // Usar formateo con zona horaria de Ecuador
        String marcaTiempo = formatearFechaEcuador(obtenerTiempoEcuador(), "HH:mm:ss");
        tvEstado.setText(String.format("[%s] %s", marcaTiempo, estado));
    }

    private void actualizarInfoServidor() {
        if (servidorEnEjecucion) {
            String direccionIp = obtenerDireccionIpLocal();
            tvInfoServidor.setText(String.format("Servidor: http://%s:8080\nPuntos finales: /api/sensor_data, /api/device_status", direccionIp));
        } else {
            tvInfoServidor.setText("Servidor: No está en ejecución");
        }
    }

    private void actualizarInfoDispositivo() {
        DeviceInfoHelper infoDispositivo = new DeviceInfoHelper(this);
        tvInfoDispositivo.setText(String.format("Dispositivo: %s\nSO: Android %s\nBatería: %d%%\nRed: %b",
                infoDispositivo.obtenerModeloDispositivo(), infoDispositivo.obtenerVersionSO(),
                infoDispositivo.obtenerNivelBateria(), infoDispositivo.estaConectadoAInternet()));
    }

    private String obtenerDireccionIpLocal() {
        WifiManager gestorWifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int direccionIp = gestorWifi.getConnectionInfo().getIpAddress();
        return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                (direccionIp & 0xff), (direccionIp >> 8 & 0xff), (direccionIp >> 16 & 0xff), (direccionIp >> 24 & 0xff));
    }

    private String obtenerIdUnicoDispositivo() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private boolean tienePermisoUbicacion() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onStatusChanged(String proveedor, int estado, Bundle extras) {}
    @Override
    public void onProviderEnabled(String proveedor) {}
    @Override
    public void onProviderDisabled(String proveedor) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detenerRecoleccionDatos();
        detenerServidorHttp();
        if (ayudanteBaseDatos != null) ayudanteBaseDatos.close();
    }
}