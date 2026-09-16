# Regla 04: Estándares de QA, Pruebas ADB y Ciclo de Lanzamientos DevOps

> [!IMPORTANT]
> Todo cambio relevante debe ser verificado en dispositivo o emulador mediante ADB sin dejar artefactos temporales, y todo despliegue OTA debe seguir el flujo estricto de versionado y empaquetado.

---

## 1. Pruebas de Calidad (QA) y Verificación en TV (ADB)

1. **Validación D-Pad en Dispositivo Físico o Emulador:**
   - Simular navegación con mando a distancia:
     - Arriba / Abajo / Izquierda / Derecha: `adb shell input keyevent 19` / `20` / `21` / `22`
     - OK / Selección: `adb shell input keyevent 23` o `66`
     - Atrás: `adb shell input keyevent 4`
   - Verificar que no existan trampas de foco ni cierres abruptos al pulsar Atrás.

2. **Certificación Visual de Foco Dorado (`#FFD700`):**
   - Captura de pantalla para inspección:
     ```powershell
     adb exec-out screencap -p > current_screen.png
     ```
   - Comprobar que el elemento enfocado sea inconfundible a 3 metros de distancia en pantalla oscura.

3. **Limpieza Inmediata de Capturas Temporales:**
   - Las capturas `.png` generadas para inspección visual deben eliminarse tras el reporte para no ensuciar el repositorio:
     ```powershell
     Get-ChildItem -Path . -Filter "*screen*.png" -Recurse -File | Remove-Item -Force
     ```

4. **Monitoreo de Logs:**
   - Filtrar excepciones no controladas y crashes:
     ```powershell
     adb logcat -d *:E | grep -i -E "pina4viewer|acestream|fatal"
     ```

---

## 2. Ciclo de Compilación y Lanzamientos (DevOps & OTA)

1. **Compilaciones Gradle:**
   - Directorio de compilación: [`pina4viewer-kotlin/`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin)
   - Compilación Debug: `./gradlew assembleDebug`
   - Compilación Release: `./gradlew assembleRelease`
   - Diagnóstico y limpieza de Daemons / Caché: `./gradlew clean --stop`

2. **Flujo de Publicación OTA (Over-The-Air):**
   - Incrementar `versionCode` (entero) y `versionName` (semver) en [`pina4viewer-kotlin/app/build.gradle.kts`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/build.gradle.kts).
   - Actualizar el manifiesto público [`version.json`](file:///d:/Users/Javi/Documents/Piña4Viewer/version.json) con la nueva versión y notas de parche.
   - Publicar el APK mediante [`scripts/upload_release.py`](file:///d:/Users/Javi/Documents/Piña4Viewer/scripts/upload_release.py) o release en GitHub.
