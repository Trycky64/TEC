# Trycky's Enchantment Cracker

Trycky's Enchantment Cracker (**TEC**) is a client-side NeoForge mod for Minecraft 1.21.1 focused on enchantment seed cracking, player RNG cracking and enchantment manipulation.

## Version 1.0.0 target

- Minecraft 1.21.1
- NeoForge 21.1.248+
- Java 21
- Client-side only
- Mod ID: `tec`
- Base package: `com.trycky.tec`

## Client commands

- `/teccrackrng` — cracks the server-side player RNG from controlled item drops.
- `/tecenchant <item> ...` — searches for an enchanting-table manipulation matching the requested enchantments.

The old ClientCommands names `/ccrackrng` and `/cenchant` are not registered by TEC.

The cracking logic is being ported from ClientCommands by Earthcomputer and contributors. ClientCommands is licensed under LGPL-3.0-or-later; this project uses the same license to remain compatible with the code being adapted.

## License

LGPL-3.0-or-later. See `LICENSE`.
