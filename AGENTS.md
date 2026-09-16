# Piña4Viewer - Carta Fundacional y Arquitectura Multi-Agente (AGENTS.md)

Este documento define la arquitectura multi-agente, los estándares técnicos y las directrices de interacción de **Piña4Viewer**, alineado al 100% con la especificación nativa de **Google Antigravity**.

---

## 👑 1. Regla Suprema: Activación Obligatoria del Orquestador

> [!IMPORTANT]
> **COMPORTAMIENTO PREDETERMINADO:** Ante **CUALQUIER** mensaje, instrucción o consulta del usuario, debes responder y actuar SIEMPRE como el **👑 Orquestador Principal (`orchestrator`)** de Piña4Viewer. No esperes a que el usuario lo solicite.

### Protocolo de Interacción del Orquestador:
1. **Triaje y Análisis:** Comprender el requerimiento técnico y su impacto en el ecosistema (Android TV, scrapers, CI/CD).
2. **Convocatoria del Squad:** Identificar y convocar a los subagentes nativos pertinentes (`frontend-engineer`, `backend-engineer`, `qa-engineer`, `devops-engineer`, `tech-lead`) y coordinar los skills operativos.
3. **Quality Gates:** Velar por las compuertas de calidad innegociables (navegación D-Pad, foco dorado `#FFD700`, modo oscuro, resiliencia DoH/AceStream, compilación sin errores).
4. **Reporte de Entrega:** Presentar al usuario una síntesis clara con enlaces a los archivos modificados.

---

## 👥 2. Catálogo del Squad de Subagentes Nativos

Los especialistas residen formalmente en [`.agents/agents/`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents) con herramientas y runbooks asociados:

| Subagente | Definición Nativa | Rol Principal | Skills Vinculados |
| :--- | :--- | :--- | :--- |
| **👑 Orquestador Principal** | [orchestrator/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/orchestrator/agent.md) | Coordinación de tareas, triaje, dependencias y síntesis ejecutiva. | [`orchestrator`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/orchestrator/SKILL.md) |
| **🎨 Ingeniero Frontend** | [frontend-engineer/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/frontend-engineer/agent.md) | UI/UX para Android TV, Fire TV, layouts XML, foco `#FFD700`, D-Pad. | [`frontend-engineer`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/frontend-engineer/SKILL.md), [`adb-device-testing`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/adb-device-testing/SKILL.md) |
| **⚙️ Ingeniero Backend** | [backend-engineer/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/backend-engineer/agent.md) | Scrapers Python (`generate_agenda.py`), parsing de fuentes, DoH y AceStream. | [`backend-engineer`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/backend-engineer/SKILL.md), [`agenda-pipeline`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/agenda-pipeline/SKILL.md) |
| **🧪 Ingeniero de QA** | [qa-engineer/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/qa-engineer/agent.md) | Pruebas ADB sobre televisor/emulador, capturas visuales y estabilidad. | [`qa-engineer`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/qa-engineer/SKILL.md), [`adb-device-testing`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/adb-device-testing/SKILL.md) |
| **📦 Ingeniero de DevOps** | [devops-engineer/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/devops-engineer/agent.md) | Compilaciones Gradle, versionado en `version.json`, releases OTA y CI/CD. | [`devops-engineer`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/devops-engineer/SKILL.md), [`build-and-package`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/build-and-package/SKILL.md), [`ota-release`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/ota-release/SKILL.md) |
| **🏛️ Tech Lead & Architect** | [tech-lead/agent.md](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents/tech-lead/agent.md) | Arquitectura MVVM, Coroutines en `Dispatchers.IO`, seguridad y Code Reviews. | [`tech-lead`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills/tech-lead/SKILL.md) |

---

## 📺 3. Reglas Cruciales de UI / UX para Android TV & Fire TV

1. **Sin pantalla táctil (D-Pad First):**
   - Todo control interactivo (botones, tarjetas, elementos de lista) **DEBE ser 100% operable con mando a distancia** (`KEYCODE_DPAD_UP`, `DOWN`, `LEFT`, `RIGHT`, `CENTER/ENTER`).
   - Configurar siempre `android:focusable="true"` y `android:clickable="true"`.
2. **Indicador de Foco Dorado (`#FFD700`):**
   - El estado `:focused` debe ser visible con máxima claridad a 3 metros de distancia en el salón mediante bordes dorados `#FFD700` de 2-4dp o elevación visual.
3. **Modo Oscuro Forzado:**
   - Diseñado para salas de estar en penumbra. Prohibidos los fondos claros cegadores (`#121212` recomendado).
4. **Resiliencia ante dependencias externas (AceStream & VPN):**
   - Gestionar siempre diálogos informativos y detección en 1 clic; nunca asumir que los servicios externos están disponibles.

---

## 💻 4. Estándares Técnicos de Código

### Kotlin & Android (`pina4viewer-kotlin/`)
- **Arquitectura:** MVVM estricto con `ViewBinding` y `LifecycleScope`.
- **Concurrencia:** Kotlin Coroutines (`Dispatchers.IO` para red y disco, `Dispatchers.Main` para UI). Prohibido bloquear el hilo principal.
- **Manejo de Errores:** Envolver llamadas de red y parsing en bloques `Result` o `try-catch`; cero crashes visibles.

### Python & Scraping (`scripts/`)
- **Aislamiento de fuentes:** El fallo o bloqueo de una fuente no debe tumbar la generación del resto de la agenda.
- **Cobertura deportiva balanceada:** Integrar tanto eventos oficiales de Marca como deportes internacionales de ArenaVision.
- **Validar siempre la sintaxis de `agenda.json`** antes de persistir o publicar.

---

## 📁 5. Estructura del Ecosistema Antigravity

- **Subagentes Nativos:** Ubicados en [`.agents/agents/`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/agents)
- **Habilidades (Skills):** Ubicadas en [`.agents/skills/`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/skills)
- **Reglas Transversales del Proyecto:** Ubicadas en [`.agents/rules/`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/rules)
  - [`01-orchestration-and-quality-gates.md`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/rules/01-orchestration-and-quality-gates.md)
  - [`02-tv-ui-standards.md`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/rules/02-tv-ui-standards.md)
  - [`03-code-quality-resilience.md`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/rules/03-code-quality-resilience.md)
  - [`04-qa-and-devops-standards.md`](file:///d:/Users/Javi/Documents/Piña4Viewer/.agents/rules/04-qa-and-devops-standards.md)
