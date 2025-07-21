package ec.edu.utn.com.example.remotemonitoringapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String ETIQUETA = "DatabaseHelper";
    private static final String NOMBRE_BASE_DATOS = "monitoreo_remoto.db";
    private static final int VERSION_BASE_DATOS = 1;

    // Tablas
    private static final String TABLA_DATOS_SENSOR = "datos_sensor";
    private static final String TABLA_TOKENS_AUTENTICACION = "tokens_autenticacion";

    // Columnas de la tabla de datos del sensor
    private static final String COLUMNA_ID = "id";
    private static final String COLUMNA_LATITUD = "latitud";
    private static final String COLUMNA_LONGITUD = "longitud";
    private static final String COLUMNA_MARCA_TIEMPO = "marca_tiempo";
    private static final String COLUMNA_ID_DISPOSITIVO = "id_dispositivo";

    // Columnas de la tabla de tokens de autenticación
    private static final String COLUMNA_ID_TOKEN = "id_token";
    private static final String COLUMNA_TOKEN = "token";
    private static final String COLUMNA_NOMBRE_USUARIO = "nombre_usuario";
    private static final String COLUMNA_CONTRASENA = "contrasena";
    private static final String COLUMNA_CREADO_EN = "creado_en";
    private static final String COLUMNA_ES_ACTIVO = "es_activo";

    public DatabaseHelper(Context context) {
        super(context, NOMBRE_BASE_DATOS, null, VERSION_BASE_DATOS);
    }

    /**
     * Método para obtener el tiempo actual en la zona horaria de Ecuador
     */
    private long obtenerTiempoEcuador() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("America/Guayaquil"));
        return calendar.getTimeInMillis();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Crear tabla de datos del sensor
        String crearTablaDatosSensor = "CREATE TABLE " + TABLA_DATOS_SENSOR + " (" +
                COLUMNA_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMNA_LATITUD + " REAL NOT NULL, " +
                COLUMNA_LONGITUD + " REAL NOT NULL, " +
                COLUMNA_MARCA_TIEMPO + " INTEGER NOT NULL, " +
                COLUMNA_ID_DISPOSITIVO + " TEXT NOT NULL" +
                ")";

        // Crear tabla de tokens de autenticación
        String crearTablaTokensAutenticacion = "CREATE TABLE " + TABLA_TOKENS_AUTENTICACION + " (" +
                COLUMNA_ID_TOKEN + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMNA_TOKEN + " TEXT UNIQUE NOT NULL, " +
                COLUMNA_NOMBRE_USUARIO + " TEXT, " +
                COLUMNA_CONTRASENA + " TEXT, " +
                COLUMNA_CREADO_EN + " INTEGER NOT NULL, " +
                COLUMNA_ES_ACTIVO + " INTEGER DEFAULT 1" +
                ")";

        db.execSQL(crearTablaDatosSensor);
        db.execSQL(crearTablaTokensAutenticacion);

        // Insertar token de autenticación por defecto
        insertarTokenAutenticacionPorDefecto(db);

        Log.d(ETIQUETA, "Base de datos creada exitosamente");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int versionAntigua, int versionNueva) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLA_DATOS_SENSOR);
        db.execSQL("DROP TABLE IF EXISTS " + TABLA_TOKENS_AUTENTICACION);
        onCreate(db);
    }

    private void insertarTokenAutenticacionPorDefecto(SQLiteDatabase db) {
        ContentValues valores = new ContentValues();
        valores.put(COLUMNA_TOKEN, "token_por_defecto_123456789");
        valores.put(COLUMNA_NOMBRE_USUARIO, "admin");
        valores.put(COLUMNA_CONTRASENA, "admin123");
        valores.put(COLUMNA_CREADO_EN, obtenerTiempoEcuador()); // Cambiado aquí
        valores.put(COLUMNA_ES_ACTIVO, 1);

        db.insert(TABLA_TOKENS_AUTENTICACION, null, valores);
        Log.d(ETIQUETA, "Token de autenticación por defecto insertado");
    }

    // Métodos de datos del sensor
    public long insertarDatosSensor(double latitud, double longitud, long marcaTiempo, String idDispositivo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues valores = new ContentValues();

        valores.put(COLUMNA_LATITUD, latitud);
        valores.put(COLUMNA_LONGITUD, longitud);
        valores.put(COLUMNA_MARCA_TIEMPO, marcaTiempo);
        valores.put(COLUMNA_ID_DISPOSITIVO, idDispositivo);

        long resultado = db.insert(TABLA_DATOS_SENSOR, null, valores);
        db.close();

        Log.d(ETIQUETA, "Datos del sensor insertados con ID: " + resultado);
        return resultado;
    }

    // Método adicional para insertar datos del sensor con tiempo automático de Ecuador
    public long insertarDatosSensorConTiempoEcuador(double latitud, double longitud, String idDispositivo) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues valores = new ContentValues();

        valores.put(COLUMNA_LATITUD, latitud);
        valores.put(COLUMNA_LONGITUD, longitud);
        valores.put(COLUMNA_MARCA_TIEMPO, obtenerTiempoEcuador()); // Usa tiempo de Ecuador automáticamente
        valores.put(COLUMNA_ID_DISPOSITIVO, idDispositivo);

        long resultado = db.insert(TABLA_DATOS_SENSOR, null, valores);
        db.close();

        Log.d(ETIQUETA, "Datos del sensor insertados con tiempo de Ecuador, ID: " + resultado);
        return resultado;
    }

    public JSONArray obtenerDatosSensorPorRangoTiempo(long tiempoInicio, long tiempoFin) {
        JSONArray arregloJson = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();

        String seleccion = COLUMNA_MARCA_TIEMPO + " >= ? AND " + COLUMNA_MARCA_TIEMPO + " <= ?";
        String[] argumentosSeleccion = {String.valueOf(tiempoInicio), String.valueOf(tiempoFin)};
        String ordenarPor = COLUMNA_MARCA_TIEMPO + " DESC";

        Cursor cursor = db.query(TABLA_DATOS_SENSOR, null, seleccion, argumentosSeleccion,
                null, null, ordenarPor);

        try {
            if (cursor.moveToFirst()) {
                do {
                    JSONObject objetoJson = new JSONObject();
                    objetoJson.put("id", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMNA_ID)));
                    objetoJson.put("latitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMNA_LATITUD)));
                    objetoJson.put("longitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMNA_LONGITUD)));
                    objetoJson.put("marca_tiempo", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMNA_MARCA_TIEMPO)));
                    objetoJson.put("id_dispositivo", cursor.getString(cursor.getColumnIndexOrThrow(COLUMNA_ID_DISPOSITIVO)));

                    arregloJson.put(objetoJson);
                } while (cursor.moveToNext());
            }
        } catch (JSONException e) {
            Log.e(ETIQUETA, "Error al crear JSON desde datos del sensor", e);
        } finally {
            cursor.close();
            db.close();
        }

        Log.d(ETIQUETA, "Recuperados " + arregloJson.length() + " registros de datos del sensor");
        return arregloJson;
    }

    public JSONArray obtenerTodosDatosSensor() {
        JSONArray arregloJson = new JSONArray();
        SQLiteDatabase db = this.getReadableDatabase();

        String ordenarPor = COLUMNA_MARCA_TIEMPO + " DESC LIMIT 100"; // Limitar a los últimos 100 registros
        Cursor cursor = db.query(TABLA_DATOS_SENSOR, null, null, null, null, null, ordenarPor);

        try {
            if (cursor.moveToFirst()) {
                do {
                    JSONObject objetoJson = new JSONObject();
                    objetoJson.put("id", cursor.getInt(cursor.getColumnIndexOrThrow(COLUMNA_ID)));
                    objetoJson.put("latitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMNA_LATITUD)));
                    objetoJson.put("longitud", cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMNA_LONGITUD)));
                    objetoJson.put("marca_tiempo", cursor.getLong(cursor.getColumnIndexOrThrow(COLUMNA_MARCA_TIEMPO)));
                    objetoJson.put("id_dispositivo", cursor.getString(cursor.getColumnIndexOrThrow(COLUMNA_ID_DISPOSITIVO)));

                    arregloJson.put(objetoJson);
                } while (cursor.moveToNext());
            }
        } catch (JSONException e) {
            Log.e(ETIQUETA, "Error al crear JSON desde datos del sensor", e);
        } finally {
            cursor.close();
            db.close();
        }

        return arregloJson;
    }

    public int obtenerConteoDatosSensor() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLA_DATOS_SENSOR, null);

        int conteo = 0;
        if (cursor.moveToFirst()) {
            conteo = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return conteo;
    }

    // Métodos de autenticación
    public boolean validarToken(String token) {
        SQLiteDatabase db = this.getReadableDatabase();
        String seleccion = COLUMNA_TOKEN + " = ? AND " + COLUMNA_ES_ACTIVO + " = 1";
        String[] argumentosSeleccion = {token};

        Cursor cursor = db.query(TABLA_TOKENS_AUTENTICACION, null, seleccion, argumentosSeleccion,
                null, null, null);

        boolean esValido = cursor.getCount() > 0;
        cursor.close();
        db.close();

        Log.d(ETIQUETA, "Resultado de validación de token: " + esValido);
        return esValido;
    }

    public boolean validarCredenciales(String nombreUsuario, String contrasena) {
        SQLiteDatabase db = this.getReadableDatabase();
        String seleccion = COLUMNA_NOMBRE_USUARIO + " = ? AND " + COLUMNA_CONTRASENA + " = ? AND " + COLUMNA_ES_ACTIVO + " = 1";
        String[] argumentosSeleccion = {nombreUsuario, contrasena};

        Cursor cursor = db.query(TABLA_TOKENS_AUTENTICACION, null, seleccion, argumentosSeleccion,
                null, null, null);

        boolean esValido = cursor.getCount() > 0;
        cursor.close();
        db.close();

        Log.d(ETIQUETA, "Resultado de validación de credenciales: " + esValido);
        return esValido;
    }

    public String obtenerTokenParaCredenciales(String nombreUsuario, String contrasena) {
        SQLiteDatabase db = this.getReadableDatabase();
        String seleccion = COLUMNA_NOMBRE_USUARIO + " = ? AND " + COLUMNA_CONTRASENA + " = ? AND " + COLUMNA_ES_ACTIVO + " = 1";
        String[] argumentosSeleccion = {nombreUsuario, contrasena};

        Cursor cursor = db.query(TABLA_TOKENS_AUTENTICACION, new String[]{COLUMNA_TOKEN},
                seleccion, argumentosSeleccion, null, null, null);

        String token = null;
        if (cursor.moveToFirst()) {
            token = cursor.getString(cursor.getColumnIndexOrThrow(COLUMNA_TOKEN));
        }

        cursor.close();
        db.close();
        return token;
    }

    public long insertarTokenAutenticacion(String token, String nombreUsuario, String contrasena) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues valores = new ContentValues();

        valores.put(COLUMNA_TOKEN, token);
        valores.put(COLUMNA_NOMBRE_USUARIO, nombreUsuario);
        valores.put(COLUMNA_CONTRASENA, contrasena);
        valores.put(COLUMNA_CREADO_EN, obtenerTiempoEcuador()); // Cambiado aquí
        valores.put(COLUMNA_ES_ACTIVO, 1);

        long resultado = db.insert(TABLA_TOKENS_AUTENTICACION, null, valores);
        db.close();

        Log.d(ETIQUETA, "Token de autenticación insertado con ID: " + resultado);
        return resultado;
    }

    public void desactivarToken(String token) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues valores = new ContentValues();
        valores.put(COLUMNA_ES_ACTIVO, 0);

        String clausulaDonde = COLUMNA_TOKEN + " = ?";
        String[] argumentosDonde = {token};

        int filasActualizadas = db.update(TABLA_TOKENS_AUTENTICACION, valores, clausulaDonde, argumentosDonde);
        db.close();

        Log.d(ETIQUETA, "Token desactivado. Filas actualizadas: " + filasActualizadas);
    }

    // Métodos de utilidad
    public void limpiarDatosSensorAntiguos(long anteriorAFecha) {
        SQLiteDatabase db = this.getWritableDatabase();
        String clausulaDonde = COLUMNA_MARCA_TIEMPO + " < ?";
        String[] argumentosDonde = {String.valueOf(anteriorAFecha)};

        int filasEliminadas = db.delete(TABLA_DATOS_SENSOR, clausulaDonde, argumentosDonde);
        db.close();

        Log.d(ETIQUETA, "Eliminados " + filasEliminadas + " registros de datos del sensor antiguos");
    }

    // Método para obtener el timestamp actual de Ecuador (útil para usar en otros códigos)
    public long obtenerTiempoActualEcuador() {
        return obtenerTiempoEcuador();
    }
}