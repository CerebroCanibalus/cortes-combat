# Cortés Combat 1.6.0

A quieter HUD, a warhammer you can tune, and a lot more weapons from other mods joining the
fight. The combat system now listens to the key you actually configured, the bars get out of
the way without jumping back in, and the numbers you read on screen are the numbers the server
charges.

Two downloads, one per version:

- **1.20.1** — `cortescombat-1.6.0-1.20.1.jar` (Forge 47.4.28)
- **1.21.1** — `cortescombat-1.6.0-1.21.1.jar` (NeoForge 21.1.250)

## The warhammer is yours to tune

Two new settings in the **server's** config file, so the admin decides and everybody plays the
same:

- **Damage cap** — the most damage a single warhammer hit can deal. `0` (the default) means no
  cap at all, exactly like before.
- **Armour penetration** — how much of the target's armour the fully charged hit ignores. `100`
  (the default) ignores all of it, `50` leaves half of it standing, and `0` makes the charged
  hit an ordinary hit against armour.

The tooltip prints the capped number, so what you read on the item is what the hit actually
does.

## The HUD gets quieter

- The crosshair and the cooldown brackets are more transparent. The crosshair used to be the
  brightest thing in the middle of the screen; now the world shows through it.
- **The bars fade out instead of blinking out.** If you stop fighting, the brackets, the charge
  bar and the guard orbs sink away over a third of a second instead of disappearing in a single
  frame. They come back in half that time, because coming back is urgent and going away is not.
- **They no longer come back because you hit something or mined a block.** Only a target in
  front of you, or a charge in progress, brings them back.
- **While the weapon reloads, they step forward.** The brackets and the bar get more present and
  a little lighter, and settle back down once the weapon is ready. The reload is exactly when
  you want to read them.

## Your "use" key is the use key

- Blocking and the special abilities now follow the **use key from the Controls menu**. Rebind
  it to a key and the shield comes up with that key, on both versions.
- One press is one special. Holding the button no longer repeats the ability several times a
  second, which is what made the arcabuz restart its reload sound in a loop.
- Typing in the chat no longer raises your guard.

## More weapons from other mods join in

- The weapon detector now also reads the tag sets other mods commonly use, so collections that
  used to be half-detected now work almost completely: of the 122 weapons Simply Swords ships in
  its sword tag, 120 now fight with their own reach, cone and charge.
- A weapon whose name does not say what it is is now classified by **what it does**: fast and
  light becomes a dagger, slow and heavy becomes a warhammer.
- Daggers, katanas, sai, chakrams and twin blades from other mods are recognised as the family
  they belong to.
- Tools are left alone. Pickaxes, hoes, buckets, bows and armour never get combat stats, and
  the generator will not write them into your files.

## Holding the button keeps trying

- If a swing was refused — your guard is still refilling, a block is in the way, the cooldown
  has not finished — the swing is retried as soon as it can be, as long as you keep the button
  down. Before, you had to let go and press again.
- The brackets pulse while you are holding and waiting, so you can see it noticed.

## What the server says is what you see

- The server's settings now travel to your client when you join. Before, the client kept its own
  copy of them, so with a damage cap the tooltip could promise a hit the server never delivered.
- The same goes for the combat rework switch: the client follows the server instead of guessing.

## Fixed

- No more crash in 1.20.1 when another mod moves you between worlds and your state does not come
  with you. You keep playing with default state instead of the server going down.

## For mod and modpack authors

- The generated weapon files now cover the families that used to be missed, they leave tools
  out, and every line still notes the material that was detected.
- Files you wrote by hand always win over the generated ones.
- The server's settings reach the client on their own: a pack that tunes the warhammer does not
  need a second copy of the config on the client side.

## Under the hood

- One codebase, two versions (1.20.1 and 1.21.1), with 1,324 automated tests.
- The server's settings travel to the client in a single message, which a client running an
  older jar can ignore instead of being disconnected.
