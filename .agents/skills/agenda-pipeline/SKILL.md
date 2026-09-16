---
name: agenda-pipeline
description: >-
  Use this skill to run, test, and validate the sports agenda scraping pipeline (generate_agenda.py),
  verify AceStream stream links, and ensure the integrity of agenda.json.
---

# Agenda Pipeline Skill

Esta habilidad define el procedimiento para ejecutar, auditar y validar el motor de recolección de eventos y canales de streaming en **Piña4Viewer**.

---

## 1. Archivos Clave
- **Script principal:** [`scripts/generate_agenda.py`](file:///d:/Users/Javi/Documents/Piña4Viewer/scripts/generate_agenda.py)
- **Archivo de salida:** [`agenda.json`](file:///d:/Users/Javi/Documents/Piña4Viewer/agenda.json)
- **Flujo automatizado:** [`.github/workflows/update_agenda.yml`](file:///d:/Users/Javi/Documents/Piña4Viewer/.github/workflows/update_agenda.yml)

---

## 2. Ejecución y Validación Local

1. **Ejecutar el scraper en local:**
   ```powershell
   python scripts/generate_agenda.py
   ```

2. **Auditoría de Integridad del JSON:**
   Comprueba que el archivo generado no esté vacío y contenga JSON válido:
   ```powershell
   python -c "import json; data=json.load(open('agenda.json', encoding='utf-8')); print(f'Total eventos: {len(data.get(\"events\", data if isinstance(data, list) else []))}')"
   ```

3. **Verificación de Fuentes:**
   El script consolida eventos de múltiples fuentes (*Elcano, RCP, Markel, Arenavision*). Si una fuente presenta errores de conexión o cambios de estructura en su web:
   - Identificar los mensajes de advertencia en consola: `Error fetching <URL>`.
   - Ajustar las expresiones regulares o cabeceras en `scripts/generate_agenda.py` sin alterar la lógica de las demás fuentes.
