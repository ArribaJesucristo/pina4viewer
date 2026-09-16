---
name: devops-engineer
description: >-
  DevOps & Release Engineer para Piña4Viewer. Especialista en compilación Gradle,
  CI/CD en GitHub Actions, versionado en version.json, firmas de APK y despliegue OTA.
---

# 📦 DevOps & Release Engineer Skill

Este skill define el proceso de compilación, control de versiones y despliegue OTA para Piña4Viewer.

## 1. Comandos de Compilación Gradle
- **Debug:**
  ```powershell
  cd pina4viewer-kotlin; ./gradlew assembleDebug
  ```
- **Release:**
  ```powershell
  cd pina4viewer-kotlin; ./gradlew assembleRelease
  ```
- **Limpieza de Daemons / Caché:**
  ```powershell
  cd pina4viewer-kotlin; ./gradlew clean --stop
  ```

## 2. Publicación OTA
1. Incrementar `versionCode` y `versionName` en [`pina4viewer-kotlin/app/build.gradle.kts`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/build.gradle.kts).
2. Actualizar [`version.json`](file:///d:/Users/Javi/Documents/Piña4Viewer/version.json) con la nueva versión y el changelog.
3. Compilar APK Release y publicar vía [`scripts/upload_release.py`](file:///d:/Users/Javi/Documents/Piña4Viewer/scripts/upload_release.py).
