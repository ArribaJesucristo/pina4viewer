---
name: qa-engineer
description: >-
  QA & Automation Test Engineer para Piña4Viewer. Especialista en pruebas sobre dispositivos
  Android TV / móviles vía ADB, navegación con mando a distancia, validación visual de foco y análisis de estabilidad.
---

# 🧪 QA & Automation Test Engineer Skill

Este skill cubre la validación integral de Piña4Viewer sobre hardware físico o emuladores utilizando ADB.

## 1. Comandos Frecuentes ADB

- Conectar dispositivo WiFi: `adb connect 192.168.1.XXX:5555`
- Navegación D-Pad:
  - Arriba: `adb shell input keyevent 19`
  - Abajo: `adb shell input keyevent 20`
  - Izquierda: `adb shell input keyevent 21`
  - Derecha: `adb shell input keyevent 22`
  - OK / Center: `adb shell input keyevent 23`
  - Back: `adb shell input keyevent 4`

## 2. Captura y Limpieza Obligatoria

1. Capturar pantalla para certificar foco `#FFD700`:
   ```powershell
   adb exec-out screencap -p > current_screen.png
   ```
2. **Limpieza tras validación:**
   ```powershell
   Get-ChildItem -Path . -Filter "*screen*.png" -Recurse -File | Remove-Item -Force
   ```
