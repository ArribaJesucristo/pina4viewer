# 🍍 Piña4Viewer

<p align="center">
  <img src="https://img.shields.io/badge/Versi%C3%B3n-v8.4.3-FFD700?style=for-the-badge&logo=android&logoColor=black" alt="Versión">
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Plataforma-Android%20%7C%20Fire%20TV%20%7C%20Google%20TV-3DDC84?style=for-the-badge&logo=android" alt="Plataforma">
  <img src="https://img.shields.io/badge/AceStream-P2P%20Ready-00A4E4?style=for-the-badge" alt="AceStream">
</p>

> **Piña4Viewer** es una aplicación nativa para Android, Amazon Fire TV Stick, Google TV y Smart TVs diseñada para acceder de forma rápida, limpia y directa a eventos deportivos y canales en vivo a través de enlaces AceStream y reproductor web integrado.

---

## ✨ Características Principales

### 📡 Multi-Fuente & Agenda en Tiempo Real
- **Actualización continua:** Agenda sincronizada automáticamente vía GitHub Actions reuniendo las mejores fuentes públicas (*Elcano, RCP, Markel, Arenavision*).
- **Más de 1.300 streams disponibles:** Opciones múltiples por evento con selector de calidad y alternativas directas.
- **Filtro rápido por deportes:** Fútbol, Baloncesto, Motor, Tenis, Ciclismo, Voleibol y más a golpe de clic.
- **Buscador instantáneo:** Encuentra cualquier partido, equipo o competición en milisegundos.

### 🛡️ Escudo Inteligente Anti-Bloqueos (Zero-Config)
- **Detección automática de restricciones:** Salta los bloqueos dinámicos impuestos por las principales operadoras de internet en España.
- **Asistente de VPN a prueba de abuelas:** Si la conexión directa a AceStream es bloqueada por tu proveedor, la app detecta tu VPN o te asiste con descarga e inicio directo en 1 toque (Psiphon / Proton).

### 📺 100% Optimizado para Android TV & Fire TV
- **Navegación nativa con mando a distancia (D-Pad):** Totalmente controlable sin necesidad de ratón o ratón virtual.
- **Foco dorado de alto contraste (`#FFD700`):** Indicador visual claro para ver en todo momento qué elemento está seleccionado desde el sofá.
- **Modo Oscuro nativo forzado:** Diseñado para pantallas de salón y televisores.

### 🔄 Actualizaciones OTA (Over-The-Air)
- Notificación y descarga automática de nuevas versiones directamente desde la aplicación sin necesidad de reinstalar manualmente por USB o exploradores externos.

---

## 📥 Instalación

### En móviles o tablets Android:
1. Descarga el último archivo **`.apk`** desde [Releases](https://github.com/ArribaJesucristo/pina4viewer/releases/latest).
2. Ábrelo y selecciona **Instalar** (permite la instalación de aplicaciones de origen desconocido si el sistema te lo solicita).

### En Amazon Fire TV Stick / Android TV:
1. Abre la app **Downloader** en tu televisor.
2. Introduce la URL directa de la última versión o accede a la sección de [Releases](https://github.com/ArribaJesucristo/pina4viewer/releases).
3. Instala el APK descargado.

> **Nota:** Para reproducir los enlaces P2P se requiere tener instalado **AceStream Engine / AceStream Media** en el dispositivo.

---

## 🛠️ Tecnologías

- **Lenguaje:** Kotlin 100%
- **Arquitectura:** Android Jetpack (MVVM, ViewBinding, ViewModel, LiveData / Flow)
- **Concurrencia:** Kotlin Coroutines & LifecycleScope
- **Red:** OkHttp con DNS sobre HTTPS / servidores personalizados
