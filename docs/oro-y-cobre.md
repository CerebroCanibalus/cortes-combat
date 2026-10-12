# Rework de ORO y COBRE — diseño cerrado, pendiente de implementación

> Extraído de `AGENTS.md`. Decisión del usuario (2026-10-07). **No implementado**: el diseño está
> cerrado y esperando su ronda. El puntero vive en `AGENTS.md` § Próximos pasos.


**Decisión del usuario (2026-10-07):** el oro y el cobre pasan de "versiones peores del hierro" a
materiales con **nicho de gameplay** propio. Mantienen su identidad de durabilidad baja, pero ganan
mecánicas que el hierro no cubre.

### ORO — "El material de encantamientos"

**Identidad:** Armas frágiles, velocidad de ataque alta, daño bajo por golpe. No son para pegar
fuerte, son para pegar **mucho** y **robar recursos**.

#### Encantamientos únicos de combate

| Encantamiento | Niveles | Efecto |
|---|---|---|
| **Toque de Midas** | 3 | Al matar un enemigo, suelta oro: N1 = 2-4 nuggets (100%), N2 = 1-2 lingotes + 1-5 nuggets (50% ambos), N3 = 1 manzana dorada (10%) + 1-3 zanahorias doradas (20%) + 1-4 lingotes (100%) + 1 manzana dorada encantada (2%) |
| **Eco de Oro** | 1 | Marca al enemigo con **Glowing** + **"Marcado de muerte"** (+20% daño recibido) |
| **Robo de Vida** | 3 | 5% del daño infligido como curación por nivel |
| **Velocidad del Viento** | 5 | +5% velocidad de movimiento por nivel (máx +25%) |
| **Peso Pluma** | 3 | **-10% velocidad de ataque** por nivel (el oro es ligero pero frágil) |
| **Golpe Fantasma** | 1-2 | 15% por nivel de probabilidad de ignorar armadura |

#### Encantamientos únicos de HERRAMIENTAS (sólo pico/pala/hacha de oro)

| Encantamiento | Niveles | Efecto |
|---|---|---|
| **Toque de Midas (Herramienta)** | 3 | Al picar un bloque con drop de mineral, convierte el drop en su versión de oro (carbón → oro, hierro → oro, etc.). N1 = 25% probabilidad, N2 = 50%, N3 = 75% + 1 nugget extra |
| **Velocidad del Viento (Herramienta)** | 3 | +10% velocidad de minería por nivel (máx +30%) |
| **Golpe Fantasma (Herramienta)** | 1 | 10% de probabilidad de que el bloque picado suelte el doble de drops |

**Razón de ser:** El oro es el material de **control del enemigo** y **ventaja económica**, no de
daño bruto. Las herramientas de oro son para **mineros que quieren oro**, no para mineros que
quieren minerales.

---

### COBRE — "El material antiséptico"

**Identidad:** Tier intermedio entre piedra y hierro, durabilidad media, pero con una mecánica
única de **purificación** y **resistencia a efectos**.

#### Mecánica 1: Armadura antiséptica

- Cada pieza de armadura de cobre equipada reduce la duración de **todos los efectos negativos**
  un **5%**.
- Las 4 piezas acumulan **20% de reducción** de efectos negativos (veneno, wither, hambre, etc.).
- **No reduce efectos positivos** (velocidad, fuerza, etc.).

#### Mecánica 2: Armas purificadoras

- Las armas de cobre dan un nuevo efecto al golpear: **"Purificación"**.
- **Purificación** hace daño pequeño pero muy rápido a:
  - **Enemigos no muertos** (zombis, esqueletos, etc.)
  - **Jugadores que lleven armadura de netherite**
- Es **daño puro** (atraviesa armaduras).
- Además **reduce su daño un 30%** (debuff temporal).

**Razón de ser:** El cobre pasa de "no tiene uso" a ser el arma de **control de no muertos** y
**anti-netherite**, con una mecánica de **sostenimiento** (armadura) que añade profundidad sin
complicar el combate.

---

### Notas de implementación

- **Toque de Midas** necesita un hook en `LivingDeathEvent` o `PlayerEvent.PlayerKill` para
  modificar los drops.
- **Purificación** necesita un efecto nuevo (MobEffect) y un hook en `LivingIncomingDamageEvent` o
  `LivingHurtEvent` para aplicar el daño puro y el debuff.
- **Armadura antiséptica** necesita un hook en `MobEffectEvent` o `LivingUpdateEvent` para reducir
  la duración de efectos negativos.
- **Encantamientos de herramientas** necesitan registrarse como `Enchantment` con `EnchantmentCategory`
  específico para herramientas de oro.
- **Sinergia con redstone** necesita leer `Level.getBestNeighborSignal()` o similar en el bloque
  adyacente al enemigo.
