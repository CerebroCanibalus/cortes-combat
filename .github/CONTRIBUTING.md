# Contributing

## Get it building

Requirements: **JDK 21** (the build pins a Java 21 toolchain and fetches one if it's missing) and Git.

```bash
git clone https://github.com/CerebroCanibalus/cortes-combat.git
cd cortes-combat
./gradlew build            # Windows: gradlew.bat build
./gradlew test             # the unit tests
./gradlew runClient        # dev client, with the mod loaded
./gradlew runServer        # dev server
./gradlew runData          # regenerate data: recipes, models, tags, language files
```

`runData` writes into `src/generated/resources`. When the data itself changed, commit its output along with the code that produced it.

If Gradle's configuration cache complains after you touched a build input, add `--no-configuration-cache` to that one command.

## Before you open a pull request

1. `./gradlew test` is green.
2. **A green build is not a playtest.** If your change touches gameplay, say in the PR what you actually did in the game — which weapon, which mob, which numbers.
3. Behaviour a test can't reach (anything that needs the game running) deserves a line in the PR saying how you checked it.
4. Keep the diff to the change. Unrelated reformatting makes review harder and hides the part that matters.

## Tests

The unit tests run on a bare JVM, which sets the rule for the whole project: **a class that names a Minecraft type cannot be loaded there** — not even from a method the test never calls. So the arithmetic, the rules and the decisions live in plain classes with no Minecraft imports, and the layer that talks to the game stays thin. If you're adding logic, put it where a test can reach it; if you can't, that's usually a sign the logic is in the wrong class.

A handful of behaviours can only be checked by reading a source file (a table that no test can instantiate, a wiring that no test can load). Those tests strip comments before they search, so that the comment explaining a bug can quote the code that caused it.

## Reporting

- Bugs and feature requests: use the [issue templates](ISSUE_TEMPLATE). The bug form asks for a log — without one there is very little to go on.
- Security problems: read [SECURITY.md](SECURITY.md), and don't open a public issue.

## License

By contributing, you agree your work is released under **GPL-3.0-only**, the same license as the project.
