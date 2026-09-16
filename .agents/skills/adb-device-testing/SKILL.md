---
name: adb-device-testing
description: >-
  Use this skill to automate testing on real Android TV, Fire TV, or emulators using ADB:
  installing APKs, launching the app, sending remote control D-Pad keyevents, capturing screens,
  and inspecting logcat.
---

# ADB Device Testing Skill

Esta habilidad proporciona el flujo de trabajo completo para validar la app **Piña4Viewer** (`com.bone.android.a4v.oficial`) directamente sobre un televisor físico, Fire TV Stick o emulador mediante ADB.

---

## 1. Conexión y Gestión de Dispositivos

- **Comprobar dispositivos conectados:**
  ```powershell
  adb devices
  ```
- **Conectar a Fire TV / Android TV por IP (WiFi):**
  ```powershell
  adb connect 192.168.1.XXX:5555
  ```

---

## 2. Instalación y Lanzamiento

- **Instalar APK compilado (sobrescribir):**
  ```powershell
  adb install -r pina4viewer-kotlin/app/build/outputs/apk/debug/app-debug.apk
  ```
- **Lanzar la aplicación:**
  ```powershell
  adb shell am start -n com.bone.android.a4v.oficial/.MainActivity
  ```
- **Cerrar / Forzar detención:**
  ```powershell
  adb shell am force-stop com.bone.android.a4v.oficial
  ```

---

## 3. Simulación de Navegación con Mando a Distancia (D-Pad)

| Acción de Mando | Código de Tecla ADB | Comando |
| :--- | :--- | :--- |
| **Arriba** | `19` (`KEYCODE_DPAD_UP`) | `adb shell input keyevent 19` |
| **Abajo** | `20` (`KEYCODE_DPAD_DOWN`) | `adb shell input keyevent 20` |
| **Izquierda** | `21` (`KEYCODE_DPAD_LEFT`) | `adb shell input keyevent 21` |
| **Derecha** | `22` (`KEYCODE_DPAD_RIGHT`) | `adb shell input keyevent 22` |
| **Aceptar / OK** | `23` (`KEYCODE_DPAD_CENTER`) | `adb shell input keyevent 23` |
| **Enter** | `66` (`KEYCODE_ENTER`) | `adb shell input keyevent 66` |
| **Atrás** | `4` (`KEYCODE_BACK`) | `adb shell input keyevent 4` |
| **Menú** | `82` (`KEYCODE_MENU`) | `adb shell input keyevent 82` |

---

## 4. Captura Visual de Pantalla (Verificación de Foco Dorado)

Para certificar visualmente que el elemento deseado tiene el foco dorado (`#FFD700`):
```powershell
adb exec-out screencap -p > current_screen.png
```

---

## 5. Extracción de Logs y Detección de Errores

- **Filtrar logs de la aplicación:**
  ```powershell
  adb logcat -d | Select-String -Pattern "com.bone.android.a4v.oficial"
  ```
- **Detectar excepciones fatales / Crashes:**
  ```powershell
  adb logcat -d *:E | Select-String -Pattern "FATAL EXCEPTION"
  ```

---

## 6. Limpieza Post-Pruebas

Al finalizar la batería de pruebas y la inspección visual, elimina siempre los archivos temporales de captura para mantener limpio el repositorio:
```powershell
Get-ChildItem -Path . -Filter "*screen*.png" -Recurse -File | Remove-Item -Force
```
