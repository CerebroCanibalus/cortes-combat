# MEMORIA.md — Cortés Combat

Memoria viva del proyecto. `AGENTS.md` = referencia técnica (cómo buildear, dónde está cada cosa). Acá va el *qué* y el *por qué*.

> No renombrar este archivo a `agents.md`: en Windows el filesystem es case-insensitive y pisaría `AGENTS.md`.

## Fase activa: 1 — Fundamentos

Estado: esqueleto del proyecto **pendiente**.

Trabajo hecho:
- Clonados los 3 mods de referencia en `refs/` (ver `AGENTS.md`). Los tres son solo lectura.
- Estructura y comandos de build verificados leyendo los 3 `build.gradle` / `gradle.properties`.
- Identificadas las trampas de plataforma: Combatify es Fabric 26.1.2 / Java 25, sus ideas hay que traducirlas a 1.21.1.

Siguiente paso: crear el esqueleto NeoForge 1.21.1 en la raíz.

## Decisiones

- **`refs/` dentro del repo pero gitignored.** Son clones de terceros con su propio historial git. Meterlos al repo del mod los contaminaría y los volvería commits nuestros.
- **Raíz = repo del mod, rama `main`, git nuevo.** El clon de Combatify que estaba en la raíz se movió a `refs/combatify` con su `.git` intacto.
- **No usar `atlas-core` ni `defaulted`.** Son dependencias de Combatify, Fabric, y no existen para NeoForge. Si necesitamos su funcionalidad (patching de componentes de item vía datapack), se reimplementa.
- **Toolchain Java 21 aunque el JDK del sistema sea 25.** 1.21.1 corre en 21; 25 solo lo necesita Combatify.

## Bugs / trampas encontradas

- `git clone` en PowerShell devuelve exit code 1 y escribe a stderr `Cloning into '.'...` aunque el clone funcione. No es un error real: verificar con `git log` antes de reaccionar.
- Combatify tiene **dos** bloques `loom {}` en su `build.gradle`; el segundo sobreescribe `accessWidenerPath` del primero. Solo aplica `combatify.classtweaker`, el `combatify.accesswidener` del primero es código muerto. No copiar esa duplicación.
- Escribir `agents.md` en la raíz **pisa `AGENTS.md`** (case-insensitive en Windows). Por eso la memoria vive acá.

## Próximos pasos

1. Esqueleto: copiar el build de `refs/rafts_combat`, `mod_id = cortes_combat`, paquete `com.cortes.combat`, `[[mixins]]` habilitado en `mods.toml`, primer commit con README/LICENSE.
2. Diseño de combate en detalle → presentarlo al usuario para aprobación antes de implementar.
3. Vertical slice: daño + feedback + HUD.
