---
name: frontend-engineer
description: >-
  Senior Android UI/UX Engineer para Piña4Viewer. Especialista en Kotlin, Android TV / Fire TV,
  layouts XML, foco visual dorado (#FFD700), navegación D-Pad y adaptabilidad de pantallas.
---

# 🎨 Senior Frontend Engineer Skill

Este skill guía la implementación de interfaces de usuario para **Android TV**, **Fire TV Stick** y **Google TV** en Piña4Viewer.

## 1. Reglas Cruciales de Implementación

- **D-Pad First:** Todos los componentes interactivos deben llevar explícitamente:
  ```xml
  android:focusable="true"
  android:clickable="true"
  ```
- **Foco Dorado `#FFD700`:** El estado enfocado debe contar con selector de estado claro (`state_focused="true"`), borde dorado de alto contraste y elevación visual.
- **RecyclerViews:** Mantener el estado del scroll y el elemento enfocado al refrescar la lista.
- **Modo Oscuro:** Diseñado para visualización en salones con baja iluminación (`#121212`).

## 2. Archivos Clave
- Layouts: [`pina4viewer-kotlin/app/src/main/res/layout/`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/src/main/res/layout)
- Colores y Temas: [`pina4viewer-kotlin/app/src/main/res/values/colors.xml`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/src/main/res/values/colors.xml)
- Actividades y Vistas: [`pina4viewer-kotlin/app/src/main/java/com/bone/android/a4v/oficial/`](file:///d:/Users/Javi/Documents/Piña4Viewer/pina4viewer-kotlin/app/src/main/java/com/bone/android/a4v/oficial)
