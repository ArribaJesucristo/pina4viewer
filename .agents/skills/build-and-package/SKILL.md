---
name: build-and-package
description: >-
  Use this skill when compiling the Android APK (debug or release) with Gradle,
  diagnosing build failures, or verifying APK size and outputs in Piña4Viewer.
---

# Build and Package Skill

Esta habilidad proporciona el procedimiento estandarizado para compilar, limpiar y empaquetar el APK de **Piña4Viewer** utilizando Gradle.

---

## 1. Requisitos Previos
- Tener instalado Java JDK 17+.
- Ubicarse en el subdirectorio [`pina4viewer-kotlin/`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin).

---

## 2. Procedimientos de Compilación

### Compilación Rápida (Debug APK)
Ideal para pruebas locales y desarrollo diario:
```powershell
cd pina4viewer-kotlin
./gradlew assembleDebug
```
- **Salida generada:** `pina4viewer-kotlin/app/build/outputs/apk/debug/app-debug.apk`

### Compilación de Producción (Release APK)
Genera el binario optimizado con Proguard/R8:
```powershell
cd pina4viewer-kotlin
./gradlew assembleRelease
```
- **Salida generada:** `pina4viewer-kotlin/app/build/outputs/apk/release/app-release.apk`

---

## 3. Resolución de Problemas Comunes

1. **Gradle Daemon corrupto o memoria insuficiente:**
   ```powershell
   cd pina4viewer-kotlin
   ./gradlew --stop
   ./gradlew clean
   ```
2. **Conflictos con dependencias o cachés:**
   - Eliminar la carpeta temporal `.gradle` dentro de `pina4viewer-kotlin/` y reconstruir el proyecto.
3. **Verificación del tamaño del APK:**
   - Comprobar que el APK no exceda tamaños anómalos (el tamaño estándar ronda los 6 MB a 8 MB).
