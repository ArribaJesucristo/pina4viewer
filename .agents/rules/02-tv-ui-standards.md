# Regla 02: Estándares de UI/UX para Android TV, Fire TV y Google TV

> [!IMPORTANT]
> Toda interfaz en Piña4Viewer está diseñada para pantallas de salón y controlada exclusivamente por mando a distancia (D-Pad).

---

## 1. Reglas Inviolables de Interacción

1. **Sin Pantalla Táctil (D-Pad First):**
   - No asumir jamás soporte táctil ni gestos de arrastre.
   - Cada botón, tarjeta de evento, elemento de lista o control de diálogo **DEBE tener**:
     ```xml
     android:focusable="true"
     android:clickable="true"
     ```
   - Utilizar atributos de navegación explícita (`android:nextFocusUp`, `android:nextFocusDown`, `android:nextFocusLeft`, `android:nextFocusRight`) siempre que la jerarquía de vistas pueda causar ambigüedad de foco.

2. **Indicador de Foco Dorado (`#FFD700`):**
   - El usuario visualiza la pantalla a una distancia de 2 a 4 metros en el salón.
   - El estado `:focused` debe ser visible inmediatamente mediante:
     - Borde dorado brillante de 2dp a 4dp (`#FFD700`).
     - Elevación o sutil escala visual (`scaleX=1.05`, `scaleY=1.05`).
     - Alto contraste contra fondos oscuros.

3. **Retención de Foco en Listas (`RecyclerView`):**
   - Al refrescar datos o actualizar la agenda, el foco debe mantenerse en el elemento actual o en una posición válida sin saltar al inicio de la pantalla ni perder la selección.

4. **Modo Oscuro Forzado para Sala de Estar:**
   - Paleta de fondos oscuros profundos (`#121212`, `#0a0a0a`) para evitar deslumbramientos en habitaciones en penumbra.
   - Tipografía blanca o gris clara (`#FFFFFF`, `#E0E0E0`) de tamaño generoso para óptima legibilidad desde el sofá.

5. **Navegación de Retorno (`KEYCODE_BACK`):**
   - La tecla de retorno del mando a distancia debe cerrar menús secundarios, popups o diálogos antes de cerrar la actividad principal.
