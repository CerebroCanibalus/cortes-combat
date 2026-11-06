# Cortés Combat 1.6.1 — Hotfix

A dedicated server could crash the moment the first player connected. That is fixed.

Two downloads, one per version:

- **1.20.1** — `cortescombat-1.6.1-1.20.1.jar` (Forge 47.4.28)
- **1.21.1** — `cortescombat-1.6.1-1.21.1.jar` (NeoForge 21.1.250)

## Fixed

- **Dedicated servers no longer crash when a player connects.** On 1.21.1, a dedicated server
  could go down the moment the first player joined, with an error about a client-side class on a
  server. Anyone running a server should take this one.

This is a hotfix. It contains nothing else: the whole of the 1.6.0 content, the quieter HUD, the
tunable warhammer and the wider weapon detection are all still there, unchanged.

## How to upgrade

Stop the server, replace the jar, start the server again. Nothing else moves: your worlds,
your configuration and your weapon files are untouched.

If you are upgrading a client, do the same — replace the jar and reconnect.

## Under the hood

- The crash came from a piece of cleanup code that was listening for a signal the client never
  receives, and asking the wrong question of it. The cleanup now listens for the signal the
  client actually gets, so the server never touches anything that only exists on a client.
- One codebase, two versions (1.20.1 and 1.21.1), with 1,328 automated tests.
