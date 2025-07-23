# 📱APP de monitoreo remoto

Una aplicación Android para monitoreo remoto de dispositivos que recopila información de GPS, batería, conectividad de red, almacenamiento y especificaciones del hardware. Incluye una API REST para acceso remoto a los datos.

## 🚀 Características

### 📊 Monitoreo de Dispositivo
- **Información de Batería**: Nivel, estado de carga, voltaje, temperatura
- **Conectividad de Red**: Estado WiFi, datos móviles, tipo de conexión
- **Almacenamiento**: Espacio interno y externo disponible/usado
- **Información del Sistema**: Versión Android, memoria RAM, CPU
- **Hardware**: Modelo, fabricante, especificaciones de pantalla
- **Ubicación GPS**: Coordenadas de latitud y longitud

### 🔐 Sistema de Autenticación
- Autenticación basada en tokens
- Credenciales de usuario seguras
- Gestión de sesiones activas

### 🌐 API REST
- Endpoints para consultar datos del dispositivo
- Autenticación por token
- Formato de respuesta JSON

## 🛠️ Requisitos del Sistema

  ### Sistema Operativo: 
Android 5.0 (Lollipop) o superior (API 21+).

 ### Herramientas: 
- Android Studio (versión 4.0 o superior)
- JDK 11 o superior
- Gradle 7.0 o superior

 ### Permisos:
- Acceso a ubicación (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION).
- Acceso a internet (INTERNET).

 ### Hardware:
Dispositivo Android con GPS habilitado.

## 📥 Instalación y Configuración

### 1. Clonar el Repositorio
```bash
git clone https://github.com/Tatiana-Quilca/RemoteMonitoringApp.git
cd RemoteMonitoringApp
```

### 2. Configurar Android Studio
1. Abrir Android Studio
2. Seleccionar "Open an existing Android Studio project"
3. Navegar hasta la carpeta del proyecto y seleccionarla
4. Esperar a que Gradle sincronice las dependencias

### 3. Configurar el Proyecto
1. **Verificar el SDK**: Ir a `File > Project Structure > SDK Location`
2. **Actualizar dependencias**: En `build.gradle (Module: app)` 

## 🏃‍♂️ Ejecutar el Proyecto

### Método 1: Dispositivo Físico
1. **Habilitar opciones de desarrollador** en el dispositivo Android
2. **Activar depuración USB**
3. Conectar el dispositivo por USB
4. En Android Studio: `Run > Run 'app'`

### Método 2: Emulador
1. **Crear AVD**: `Tools > AVD Manager > Create Virtual Device`
2. **Seleccionar dispositivo**: Recomendado Pixel con API 30+
3. **Iniciar emulador**
4. En Android Studio: `Run > Run 'app'`

# Generar APK de release
Build > Generate App Bundles or APKs > Generate APKs

**Credenciales por defecto:**
- Usuario: `admin`
- Contraseña: `admin123`
- Token: `token_por_defecto_123456789`

## 🌐 API Endpoints

### Autenticación
```
POST /api/auth
Body: {"username": "admin", "password": "admin123"}
Response: {"token": "token_string"}
```

### Obtener Estado del Dispositivo
```
GET /api/device_status
Headers: {"Authorization": "Bearer token_string"}
Response: {
  "bateria": {...},
  "red": {...},
  "almacenamiento": {...},
  "sistema": {...},
  "hardware": {...}
}
```

### Obtener Datos GPS
```
GET /api/sensor_data?start=timestamp&end=timestamp
Headers: {"Authorization": "Bearer token_string"}
Response: [
  {
    "id": 1,
    "latitud": -0.123456,
    "longitud": -78.654321,
    "marca_tiempo": 1640995200000,
    "id_dispositivo": "device123"
  }
]
```

## 📱 Uso de la Aplicación

### Primera Ejecución
1. **Conceder permisos** de ubicación cuando se solicite
2. **Verificar conectividad** a internet para funciones remotas
3. **Configurar intervalos** de monitoreo según necesidades

### Funcionalidades Principales
- **Vista Principal**: estado actual del dispositivo
- **Configuraciones**: Ajustar intervalos de monitoreo
- **Historial**: Ver datos históricos de GPS y rendimiento
- **API**: Acceso remoto mediante endpoints REST


### Problemas Comunes

#### La app no puede obtener ubicación
- Verificar que los permisos de ubicación estén concedidos
- Asegurarse de que el GPS esté activado
- Comprobar que la app tenga acceso a ubicación en segundo plano

#### Error de conexión a base de datos
- Verificar espacio de almacenamiento disponible
- Reiniciar la aplicación
- Limpiar caché
#### API no responde
- Verificar conexión a internet
- Comprobar credenciales de autenticación

## Vista de la APP 
<img src="https://github.com/user-attachments/assets/e256ed71-e1c1-472c-a7a9-ae55de13ec14" alt="Vista previa de la aplicación" width="150">
<img src="https://github.com/user-attachments/assets/be0507cc-2de8-464e-8320-e906c41a3f24" alt="Vista previa de la aplicación" width="150">
<img src="https://github.com/user-attachments/assets/790ce22c-d27b-4700-bc19-a57a931b4baf" alt="Vista previa de la aplicación" width="150">



