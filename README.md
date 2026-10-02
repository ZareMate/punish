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

## Java API

Other server-side mods can depend on Punish and use:

    import com.zaremate.punish.api.PunishApi;
    import com.zaremate.punish.api.Punishment;
    import com.zaremate.punish.api.PunishmentType;

    List<Punishment> current = PunishApi.getCurrentPunishments(server, playerUuid);

To create a punishment:

    Punishment punishment = PunishApi.addPunishment(
        server,
        player.getUUID(),
        player.getGameProfile().getName(),
        PunishmentType.BAN,
        7L * 24 * 60 * 60 * 1000,
        "Rule violation",
        "MyMod"
    );

Use durationMillis = 0 for a permanent punishment.

The generic overload also supports IP-based punishments and silent announcements:

    Punishment punishment = PunishApi.addPunishment(
        server,
        playerUuid,
        playerName,
        PunishmentType.MUTE,
        true,
        playerIp,
        30L * 60 * 1000,
        "Chat violation",
        "MyMod",
        true
    );

getCurrentPunishments(...) includes active UUID punishments and active IP punishments that apply to the player's current/known IP addresses.