---
name: tech-lead
description: >-
  Tech Lead & Software Architect para Piña4Viewer. Supervisa la arquitectura (MVVM, Coroutines,
  Jetpack), revisión de código (Code Reviews), seguridad, rendimiento y buenas prácticas.
---

# 🏛️ Tech Lead & Software Architect Skill

Este skill define la supervisión técnica, diseño arquitectónico y auditoría de código para Piña4Viewer.

## 1. Principios de Arquitectura MVVM
- Vistas pasivas (`Activity`/`Fragment`): solo observan estado y envían eventos.
- `ViewModel` maneja la lógica de presentación con `StateFlow` o `LiveData`.
- Toda operación costosa o de I/O debe delegarse a `Dispatchers.IO`.

## 2. Checklist de Code Review
- [ ] ¿Es 100% compatible con mando D-Pad de Android TV?
- [ ] ¿Hay riesgo de `NullPointerException` o caída no controlada?
- [ ] ¿Se gestionan adecuadamente los ciclos de vida de corrutinas (`viewModelScope`)?
- [ ] ¿Se preserva la seguridad y no se exponen credenciales en claro?
