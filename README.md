# Punish

A server-side NeoForge punishment system for Minecraft 1.21.1, rewritten from the original KubeJS implementation.

## Commands
- `/ban`, `/tempban`, `/ipban`, `/tempipban`
- `/unban`, `/unbanip`
- `/mute`, `/tempmute`, `/ipmute`, `/unmute`
- `/warn`, `/ipwarn`, `/unwarn`, `/warnings`
- `/kick`, `/history`, `/checkban`, `/checkmute`, `/dupeip`, `/staffhistory`
- `/punish`
- Vanilla aliases: `/pardon`, `/ban-ip`, `/pardon-ip`

All punishment commands require permission level 2.

## Storage
Punishment data is stored in `config/punish.json`.

## Duration format
`30m`, `1h`, `3d`, `2w`, `1mo`, `1y` and combinations such as `1d12h`.

## Security
Discord webhook credentials from the original KubeJS script are deliberately not copied into the mod.

## Migration
The legacy script is not copied into this repository because it contained live webhook credentials. A migration layer for an existing `local/kubejs/punishments.json` should be added after validating the existing server data.
