# Regla 03: Estándares de Arquitectura, Concurrencia y Resiliencia

> [!IMPORTANT]
> El código debe ser tolerante a caídas de red, fuentes P2P volátiles y restricciones de hardware en dispositivos de TV de gama de entrada (Fire TV Stick Lite, cajas Android TV).

---

## 1. Kotlin & Android

1. **Arquitectura MVVM Estricta:**
   - La capa de presentación (`Activity`/`Fragment`) solo observa datos mediante `LiveData` o `StateFlow`.
   - Cero lógica de negocio, parseo o llamadas HTTP directas dentro de las Vistas.
   - Repositorios con inyección de dependencias limpia o instancias singleton bien acotadas.

2. **Gestión de Corrutinas y Concurrencia:**
   - Todo trabajo de I/O (lectura de JSON, requests de red, consultas de base de datos) debe ejecutarse obligatoriamente bajo `Dispatchers.IO`.
   - El hilo principal (`Dispatchers.Main`) está reservado exclusivamente para renderizado y actualización de vistas.
   - No utilizar `GlobalScope`; vincular todas las corrutinas a `viewModelScope` o `lifecycleScope`.

3. **Manejo Seguro de Errores:**
   - Toda llamada de red o lectura de stream AceStream debe envolverse en bloques `try-catch` o `Result.runCatching`.
   - Ante la caída de un hash de AceStream o fallo de conexión, presentar un diálogo o mensaje explicativo sin provocar un crash (cero excepciones no capturadas).

---

## 2. Python & Pipeline de Scraping

1. **Aislamiento por Fuente:**
   - En `scripts/generate_agenda.py`, cada fuente (Elcano, RCP, Markel, Arenavision) debe ejecutarse de forma aislada.
   - Si una fuente falla por timeout, DNS o cambio de HTML, se registra la advertencia y el script continúa procesando el resto de fuentes.

2. **Garantía del Esquema JSON:**
   - Nunca escribir en `agenda.json` una salida corrupta o vacía por error no controlado.
   - Validar sintaxis y estructura antes de persistir o publicar en GitHub Actions.
