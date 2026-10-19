package com.zaremate.punish;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Registered NeoForge permissions, resolved by the server's permission provider (e.g. LuckPerms). */
public final class PunishPermissions {
    private static final Map<String, PermissionNode<Boolean>> COMMANDS = Stream.of(
            "ban", "ipban", "mute", "ipmute", "warn", "ipwarn",
            "warnings", "kick", "history", "checkban", "checkmute", "dupeip",
            "staffhistory", "punish")
            .collect(Collectors.toUnmodifiableMap(name -> name, PunishPermissions::node));

    public static final PermissionNode<Boolean> NOTIFY_SILENT = node("notify.silent");
    public static final PermissionNode<Boolean> ALERTS_ALTS = node("alerts.alts");

    private PunishPermissions() {}

    private static PermissionNode<Boolean> node(String name) {
        return new PermissionNode<>(PunishMod.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(4));
    }

    public static void register(PermissionGatherEvent.Nodes event) {
        COMMANDS.values().forEach(event::addNodes);
        event.addNodes(NOTIFY_SILENT, ALERTS_ALTS);
    }

    public static boolean command(CommandSourceStack source, String command) {
        String permission = switch (command) {
            case "tempban", "unban" -> "ban";
            case "tempipban", "unbanip" -> "ipban";
            case "tempmute", "unmute" -> "mute";
            case "unwarn", "warn_remove" -> "warn";
            case "unoffense" -> "punish";
            default -> command;
        };
        PermissionNode<Boolean> node = Objects.requireNonNull(COMMANDS.get(permission), "Unknown punishment command");
        // Console and command blocks retain their vanilla permission-level checks.
        return source.getEntity() instanceof ServerPlayer player
                ? has(player, node) : source.hasPermission(4);
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }
}
