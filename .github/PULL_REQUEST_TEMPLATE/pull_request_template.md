## What this changes

<!-- One or two lines. What was broken, or what's new. -->

## Why

<!-- The reason, not the diff. If it fixes an issue, link it. -->

## How you verified it

- [ ] `./gradlew test` is green
- [ ] I ran it in the game <!-- single-player, or a server -->

<!-- If the change is gameplay-facing, say what you did in-game: which weapon, which mob, which
     numbers. A green build is not a playtest, and the PR is where that gets recorded. -->

## Checklist

- [ ] The diff is only the change (no unrelated reformatting)
- [ ] Logic that a test could reach lives where a bare-JVM test can load it, not inside a class that names a Minecraft type
- [ ] If this touches data (recipes, models, tags, language files), `./gradlew runData` was run and its output is committed
- [ ] I agree to license this under GPL-3.0-only
