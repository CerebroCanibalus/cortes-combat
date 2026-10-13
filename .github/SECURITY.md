# Security Policy

## Supported versions

Security fixes land on the current release line.

| Version | Supported |
| --- | --- |
| 0.1.x | :white_check_mark: |
| anything older | :x: |

## Reporting a vulnerability

**Please don't open a public issue for a security problem.**

> **This repository is private, so GitHub's private vulnerability reporting is not switched on yet** —
> the setting needs a public repository (or GitHub Advanced Security), and until it is public that
> link answers 404. While the repository stays private, open a Discussion: in a private repository a
> Discussion is only readable by people who already have access. The link below becomes the preferred
> channel the day the repository is published.

Use GitHub's [private vulnerability reporting](https://github.com/CerebroCanibalus/cortes-combat/security/advisories/new) to start a private thread with the maintainer. Include:

- what the problem is, and what someone gets out of exploiting it;
- the version, and how you're running it — single-player, or a server, and if a server, whether it's reachable by people you don't trust;
- the smallest reproduction you have. A log or a packet dump beats a description.

You'll get an acknowledgement, and credit when the fix ships, unless you'd rather stay anonymous.

## What counts

This is a Minecraft mod, so the surface worth attacking is narrow and specific:

- **Network payloads.** A client sends combat packets to the server. A packet that crashes the server, desyncs it, or makes the server accept something the client shouldn't be able to decide is in scope.
- **Config and data parsing.** Weapon packs and datapacks are YAML, JSON and tag files that the mod reads from disk. A file that makes the loader execute something rather than read it is in scope; a file that makes the game crash in single-player is a bug report, not a vulnerability.
- **Command permissions.** Anything reachable from a command that should need elevated permissions and doesn't.

Out of scope: bugs that need an already-compromised machine, and crashes a player can only trigger against themselves in single-player. Those are still welcome — as a normal bug report.
