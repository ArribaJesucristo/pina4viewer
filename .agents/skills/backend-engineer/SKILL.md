---
name: backend-engineer
description: >-
  Backend & Data Pipeline Engineer para Piña4Viewer. Especialista en scripts Python, scrapers
  deportivos (generate_agenda.py), estructura agenda.json, protocolos de red, DoH y enlaces AceStream.
---

# ⚙️ Backend & Data Pipeline Engineer Skill

Este skill define los protocolos para mantener el backend de extracción de eventos deportivos, la resiliencia ante bloqueos de ISP y el flujo de datos de AceStream.

## 1. Mantenimiento del Scraper

- **Archivo Principal:** [`scripts/generate_agenda.py`](file:///d:/Users/Javi/Documents/Piña4Viewer/scripts/generate_agenda.py)
- **Aislamiento de Errores:** Envolver cada scraper de fuente (*Elcano, RCP, Markel, Arenavision*) en su propio bloque `try-catch`. Si una fuente cae, registrar el log y seguir con las demás.
- **Validación Local:**
  ```powershell
  python scripts/generate_agenda.py --test
  ```

## 2. Validación de `agenda.json`
- Comprobar que el archivo generado sea un JSON válido con campos normalizados (`title`, `time`, `category`, `channels`).
- Verificar que los hashes de AceStream contengan identificadores válidos sin URLs malformadas.
