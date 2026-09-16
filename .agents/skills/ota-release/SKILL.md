---
name: ota-release
description: >-
  Use this skill to perform an Over-The-Air (OTA) release for Piña4Viewer:
  bumping versionCode and versionName, updating version.json with changelog,
  building the release APK, and publishing the release to GitHub using upload_release.py.
---

# OTA Release Skill

Esta habilidad guía el procedimiento completo para preparar y publicar una nueva versión oficial de **Piña4Viewer** distribuida mediante el sistema OTA (Over-The-Air).

---

## 1. Archivos Involucrados
- Configuración de compilación: [`pina4viewer-kotlin/app/build.gradle.kts`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/build.gradle.kts)
- Manifiesto OTA público: [`version.json`](file:///d:/Users/Javi/Documents/Piña4Viewer/version.json)
- Script de publicación: [`scripts/upload_release.py`](file:///d:/Users/Javi/Documents/Piña4Viewer/scripts/upload_release.py)
- README principal: [`README.md`](file:///d:/Users/Javi/Documents/Piña4Viewer/README.md)

---

## 2. Procedimiento Paso a Paso

### Paso 1: Incrementar Versión en Gradle
Edita [`pina4viewer-kotlin/app/build.gradle.kts`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/build.gradle.kts):
- Incrementa `versionCode` (ejemplo: de `2043` a `2044`).
- Actualiza `versionName` (ejemplo: de `"8.4.3"` a `"8.4.4"`).

### Paso 2: Actualizar el Manifiesto OTA (`version.json`)
Edita [`version.json`](file:///d:/Users/Javi/Documents/Piña4Viewer/version.json):
- Ajusta `versionCode` y `versionName` para que coincidan exactamente con Gradle.
- Actualiza `apkUrl` apuntando a la nueva tag:
  `"https://github.com/ArribaJesucristo/pina4viewer/releases/download/v8.4.4/pina4viewer-v8.4.4.apk"`
- Escribe el `changelog` con las mejoras o correcciones introducidas.

### Paso 3: Compilar el APK de Release
```powershell
cd pina4viewer-kotlin
./gradlew assembleRelease
cd ..
```
- Copia o renombra el APK generado a la raíz si es necesario:
  ```powershell
  Copy-Item pina4viewer-kotlin/app/build/outputs/apk/release/app-release.apk pina4viewer-v8.4.4.apk
  ```

### Paso 4: Publicar el Release en GitHub
Ejecuta el script de publicación:
```powershell
python scripts/upload_release.py
```
*Nota: Asegúrate de tener configurada la variable de entorno `GITHUB_TOKEN` o que git tenga permisos de push.*
