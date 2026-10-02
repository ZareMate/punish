package com.zaremate.punish.api;

import com.mojang.authlib.GameProfile;
import com.zaremate.punish.PunishMod;
import com.zaremate.punish.PunishService;
import com.zaremate.punish.data.PunishmentRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public integration API for other server-side mods.
 */
public final class PunishApi {
    private PunishApi() {}

    /**
     * Returns all currently applicable punishments for a player.
     * This includes UUID punishments and IP punishments for the player's
     * current/known IP addresses.
     */
    public static List<Punishment> getCurrentPunishments(MinecraftServer server, UUID playerUuid) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");

        PunishService service = service(server);
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        String ip = player == null ? null : service.ip(player);

        return service.currentPunishments(playerUuid.toString(), ip).stream()
                .map(PunishApi::toApi)
                .toList();
    }

    /** Returns current punishments for an online player. */
    public static List<Punishment> getCurrentPunishments(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = Objects.requireNonNull(player.getServer(), "player.getServer()");
        return getCurrentPunishments(server, player.getUUID());
    }

    /** Returns a current punishment of the requested type, if one exists. */
    public static Optional<Punishment> getCurrentPunishment(
            MinecraftServer server, UUID playerUuid, PunishmentType type) {
        Objects.requireNonNull(type, "type");
        return getCurrentPunishments(server, playerUuid).stream()
                .filter(p -> p.type() == type)
                .findFirst();
    }

    /**
     * Creates a punishment through the same persistence/enforcement path used
     * by the Punish mod commands.
     *
     * @param ipBased target by IP instead of UUID
     * @param ip target IP when ipBased is true
     * @param durationMillis duration in milliseconds; 0 means permanent
     * @param reason reason stored in the case
     * @param by issuer/mod name stored in the case
     * @param silent suppress public announcement
     */
    public static Punishment addPunishment(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            PunishmentType type,
            boolean ipBased,
            String ip,
            long durationMillis,
            String reason,
            String by,
            boolean silent
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(by, "by");

        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis cannot be negative");
        if (ipBased && (ip == null || ip.isBlank()))
            throw new IllegalArgumentException("ip is required for an IP-based punishment");
        if (type == PunishmentType.KICK && ipBased)
            throw new IllegalArgumentException("Kick cannot be IP-based");

        PunishService service = service(server);
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);

        String targetIp = ipBased ? ip : player == null ? null : service.ip(player);
        GameProfile profile = player != null
                ? player.getGameProfile()
                : new GameProfile(playerUuid, playerName);

        PunishService.Target target = new PunishService.Target(
                playerName,
                playerUuid.toString(),
                targetIp,
                profile,
                player
        );

        PunishmentRecord record = switch (type) {
            case BAN -> service.addApiBan(server, target, ipBased, durationMillis, reason, by, silent);
            case MUTE -> service.addApiMute(server, target, ipBased, durationMillis, reason, by, silent);
            case WARN -> service.addApiWarn(server, target, ipBased, reason, by, silent);
            case KICK -> service.addApiKick(target, reason, by, silent);
        };

        return toApi(record);
    }

    /** Convenience overload for a normal UUID-based player punishment. */
    public static Punishment addPunishment(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            PunishmentType type,
            long durationMillis,
            String reason,
            String by
    ) {
        return addPunishment(server, playerUuid, playerName, type, false, null,
                durationMillis, reason, by, false);
    }

    /** Convenience overload for an online player. */
    public static Punishment addPunishment(
            ServerPlayer player,
            PunishmentType type,
            long durationMillis,
            String reason,
            String by
    ) {
        Objects.requireNonNull(player, "player");
        return addPunishment(player.getServer(), player.getUUID(),
                player.getGameProfile().getName(), type, durationMillis, reason, by);
    }


    /**
     * Applies a configured offense using the same escalation logic as /punish.
     *
     * Example: addPunishment(server, uuid, name, "xray", "TSA AntiCheat")
     * records an X-Ray offense and applies its configured punishment step.
     */
    public static List<Punishment> addPunishment(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            String offense,
            String by
    ) {
        return addPunishment(server, playerUuid, playerName, offense, "", by, false);
    }

    /**
     * Applies a configured offense with the same optional duration/note syntax
     * accepted after an offense in /punish.
     *
     * Example rest values: "3d manual review", "-s", "2d suspicious mining".
     */
    public static List<Punishment> addPunishment(
            MinecraftServer server,
            UUID playerUuid,
            String playerName,
            String offense,
            String rest,
            String by,
            boolean silent
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(offense, "offense");
        Objects.requireNonNull(by, "by");

        PunishService service = service(server);
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        String targetIp = player == null ? null : service.ip(player);
        GameProfile profile = player != null
                ? player.getGameProfile()
                : new GameProfile(playerUuid, playerName);

        PunishService.Target target = new PunishService.Target(
                playerName,
                playerUuid.toString(),
                targetIp,
                profile,
                player
        );

        boolean effectiveSilent = silent || com.zaremate.punish.util.CommandUtil.silent(rest);
        return service.applyOffense(
                        server,
                        target,
                        offense,
                        com.zaremate.punish.util.CommandUtil.cleanSilent(rest),
                        by,
                        effectiveSilent
                )
                .stream()
                .map(PunishApi::toApi)
                .toList();
    }

    /** Convenience overload for a configured offense on an online player. */
    public static List<Punishment> addPunishment(
            ServerPlayer player,
            String offense,
            String by
    ) {
        Objects.requireNonNull(player, "player");
        return addPunishment(
                Objects.requireNonNull(player.getServer(), "player.getServer()"),
                player.getUUID(),
                player.getGameProfile().getName(),
                offense,
                by
        );
    }

    private static PunishService service(MinecraftServer server) {
        return PunishMod.service(server.getServerDirectory().toAbsolutePath());
    }

    private static Punishment toApi(PunishmentRecord record) {
        UUID uuid = null;
        if (record.uuid != null) {
            try {
                uuid = UUID.fromString(record.uuid);
            } catch (IllegalArgumentException ignored) {
                // Keep legacy malformed records queryable.
            }
        }

        PunishmentType type = switch (record.type.toLowerCase()) {
            case "ban" -> PunishmentType.BAN;
            case "mute" -> PunishmentType.MUTE;
            case "warn" -> PunishmentType.WARN;
            case "kick" -> PunishmentType.KICK;
            default -> throw new IllegalStateException("Unknown punishment type: " + record.type);
        };

        return new Punishment(
                record.id,
                type,
                record.ipBased,
                uuid,
                record.name,
                record.ip,
                record.reason,
                record.by,
                record.at,
                record.until,
                record.active,
                record.silent,
                record.offense,
                record.offenseNumber
        );
    }
}
