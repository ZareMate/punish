package com.zaremate.punish.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zaremate.punish.PunishMod;
import com.zaremate.punish.PunishConfig;
import com.zaremate.punish.DiscordLogger;
import com.zaremate.punish.PunishService;
import com.zaremate.punish.data.Offense;
import com.zaremate.punish.data.PunishmentRecord;
import com.zaremate.punish.util.CommandUtil;
import com.zaremate.punish.util.DurationUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.stream.Collectors;

public final class PunishCommands {
    private PunishCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        registerBan(d, "ban", false, false);
        registerBan(d, "tempban", true, false);
        registerBan(d, "ipban", false, true);
        registerBan(d, "tempipban", true, true);
        registerUnban(d, "unban", false);
        registerUnban(d, "unbanip", true);
        registerMute(d, "mute", false, false);
        registerMute(d, "tempmute", true, false);
        registerMute(d, "ipmute", false, true);
        registerUnmute(d);
        registerWarn(d, "warn", false);
        registerWarn(d, "ipwarn", true);
        registerUnwarn(d);
        registerWarnings(d);
        registerKick(d);
        registerHistory(d);
        registerCheck(d, "checkban", "ban");
        registerCheck(d, "checkmute", "mute");
        registerDupeIp(d);
        registerStaffHistory(d);
        registerPunish(d);

        registerAlias(d, "pardon", "unban", false);
        registerAlias(d, "ban-ip", "ipban", true);
        registerAlias(d, "pardon-ip", "unbanip", true);
    }

    private static void registerBan(CommandDispatcher<CommandSourceStack> d, String name, boolean durationRequired, boolean ipMode) {
        d.register(Commands.literal(name).requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(c -> executeBan(c, name, durationRequired, ipMode))
                        .then(Commands.argument("rest", StringArgumentType.greedyString())
                                .executes(c -> executeBan(c, name, durationRequired, ipMode)))));
    }

    private static int executeBan(CommandContext<CommandSourceStack> c, String command, boolean durationRequired, boolean ipMode) {
        String targetValue = StringArgumentType.getString(c, "target");
        String rest = getOptional(c, "rest");
        boolean silent = CommandUtil.silent(rest);
        String clean = CommandUtil.cleanSilent(rest);
        String[] parts = clean.isBlank() ? new String[0] : clean.split("\\s+", 2);

        long duration = 0;
        String reason = "No reason given";
        if (parts.length > 0) {
            long parsed = DurationUtil.parse(parts[0]);
            if (parsed >= 0) {
                duration = parsed;
                if (parts.length > 1) reason = parts[1];
            } else {
                reason = clean;
            }
        }
        if (durationRequired && duration <= 0) return fail(c, "A duration is required.");
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetValue, ipMode);
        if (target.isEmpty()) return fail(c, "Unknown player or IP.");
        if (ipMode && service.isLocalOrProxyIp(target.get().ip())) return fail(c, "Refusing to punish a local/proxy address.");
        service.applyBan(c.getSource().getServer(), c.getSource(), target.get(), ipMode, duration, reason, silent);
        return 1;
    }

    private static void registerUnban(CommandDispatcher<CommandSourceStack> d, String name, boolean ipMode) {
        d.register(Commands.literal(name).requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> opt = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), ipMode);
                    if (opt.isEmpty()) return fail(c, "Unknown player or IP.");
                    PunishService.Target target = opt.get();
                    if (ipMode) c.getSource().getServer().getPlayerList().getIpBans().remove(target.ip());
                    else c.getSource().getServer().getPlayerList().getBans().remove(target.profile());
                    int ended = 0;
                    for (PunishmentRecord r : service.db().records()) {
                        if (!r.active || !r.type.equals("ban") || r.ipBased != ipMode) continue;
                        if (Objects.equals(r.uuid, target.uuid()) && Objects.equals(r.ip, target.ip())) {
                            r.active = false;
                            r.removedBy = c.getSource().getTextName();
                            r.removedAt = System.currentTimeMillis();
                            ended++;
                        }
                    }
                    service.save();
                    c.getSource().sendSuccess(() -> Component.literal("Unbanned " + (ipMode ? target.ip() : target.name()) + "."), true);
                    return ended > 0 ? 1 : 0;
                })));
    }

    private static void registerMute(CommandDispatcher<CommandSourceStack> d, String name, boolean durationRequired, boolean ipMode) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(s -> s.hasPermission(2));
        root.then(Commands.argument("target", StringArgumentType.word())
                .executes(c -> executeMute(c, durationRequired, ipMode, ""))
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .executes(c -> executeMute(c, durationRequired, ipMode, StringArgumentType.getString(c, "rest")))));
        d.register(root);
    }

    private static int executeMute(CommandContext<CommandSourceStack> c, boolean durationRequired, boolean ipMode, String rest) {
        String targetName = StringArgumentType.getString(c, "target");
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetName, ipMode);
        if (target.isEmpty()) return fail(c, "Unknown player or IP.");
        if (ipMode && service.isLocalOrProxyIp(target.get().ip())) return fail(c, "Refusing to punish a local/proxy address.");
        boolean silent = CommandUtil.silent(rest);
        String clean = CommandUtil.cleanSilent(rest);
        String[] parts = clean.isBlank() ? new String[0] : clean.split("\\s+", 2);
        long duration = 0;
        String reason = "No reason given";
        if (parts.length > 0) {
            long parsed = DurationUtil.parse(parts[0]);
            if (parsed >= 0) {
                duration = parsed;
                if (parts.length > 1) reason = parts[1];
            } else reason = clean;
        }
        if (durationRequired && duration <= 0) return fail(c, "A duration is required.");
        service.applyMute(c.getSource().getServer(), c.getSource(), target.get(), ipMode, duration, reason, silent);
        return 1;
    }

    private static void registerUnmute(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("unmute").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    int ended = 0;
                    for (PunishmentRecord r : service.db().records()) {
                        if (!r.active || !r.type.equals("mute")) continue;
                        if ((!r.ipBased && Objects.equals(r.uuid, target.get().uuid()))
                                || (r.ipBased && Objects.equals(r.ip, target.get().ip()))) {
                            r.active = false;
                            r.removedBy = c.getSource().getTextName();
                            r.removedAt = System.currentTimeMillis();
                            ended++;
                        }
                    }
                    service.save();
                    if (target.get().player() != null) target.get().player().sendSystemMessage(Component.literal("You are no longer muted."));
                    return ended;
                })));
    }

    private static void registerWarn(CommandDispatcher<CommandSourceStack> d, String name, boolean ipMode) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(s -> s.hasPermission(2));
        root.then(Commands.argument("target", StringArgumentType.word())
                .executes(c -> executeWarn(c, ipMode, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(c -> executeWarn(c, ipMode, StringArgumentType.getString(c, "reason")))));
        d.register(root);
    }

    private static int executeWarn(CommandContext<CommandSourceStack> c, boolean ipMode, String rawReason) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(),
                StringArgumentType.getString(c, "target"), ipMode);
        if (target.isEmpty()) return fail(c, "Unknown player or IP.");
        if (ipMode && service.isLocalOrProxyIp(target.get().ip())) return fail(c, "Refusing to punish a local/proxy address.");
        boolean silent = CommandUtil.silent(rawReason);
        String reason = CommandUtil.cleanSilent(rawReason);
        service.applyWarn(c.getSource().getServer(), c.getSource(), target.get(), ipMode,
                reason.isBlank() ? "No reason given" : reason, silent);
        return 1;
    }

    private static void registerUnwarn(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("unwarn").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    PunishmentRecord latest = null;
                    for (PunishmentRecord r : service.db().records()) {
                        if (r.live(System.currentTimeMillis()) && r.type.equals("warn") && !r.ipBased
                                && Objects.equals(r.uuid, target.get().uuid())) latest = r;
                    }
                    if (latest == null) return fail(c, "Player has no active warning.");
                    latest.active = false;
                    latest.removedBy = c.getSource().getTextName();
                    latest.removedAt = System.currentTimeMillis();
                    service.save();
                    return 1;
                })));
    }

    private static void registerWarnings(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warnings").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    int count = 0;
                    for (PunishmentRecord r : service.db().records()) {
                        if (r.type.equals("warn") && r.active && Objects.equals(r.uuid, target.get().uuid())) {
                            c.getSource().sendSuccess(() -> Component.literal("#" + r.id + " " + r.reason + " by " + r.by), false);
                            count++;
                        }
                    }
                    final int totalWarnings = count;
                    c.getSource().sendSuccess(() -> Component.literal("Warnings: " + totalWarnings), false);
                    return totalWarnings;
                })));
    }

    private static void registerKick(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kick").requires(s -> s.hasPermission(2));
        root.then(Commands.argument("target", StringArgumentType.word())
                .executes(c -> executeKick(c, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(c -> executeKick(c, StringArgumentType.getString(c, "reason")))));
        d.register(root);
    }

    private static int executeKick(CommandContext<CommandSourceStack> c, String rawReason) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(),
                StringArgumentType.getString(c, "target"), false);
        if (target.isEmpty() || target.get().player() == null) return fail(c, "Player is not online.");
        String reason = CommandUtil.cleanSilent(rawReason);
        service.applyKick(c.getSource(), target.get(), reason.isBlank() ? "No reason given" : reason,
                CommandUtil.silent(rawReason));
        return 1;
    }

    private static void registerHistory(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("history").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    List<PunishmentRecord> list = service.db().records().stream()
                            .filter(r -> Objects.equals(r.uuid, target.get().uuid())
                                    || (r.ipBased && target.get().ip() != null && target.get().ip().equals(r.ip)))
                            .toList();
                    list.stream().skip(Math.max(0, list.size() - 10)).forEach(r ->
                            c.getSource().sendSuccess(() -> Component.literal("#" + r.id + " [" + r.type + "] " + r.reason + " by " + r.by), false));
                    return list.size();
                })));
    }

    private static void registerCheck(CommandDispatcher<CommandSourceStack> d, String command, String type) {
        d.register(Commands.literal(command).requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    Optional<PunishmentRecord> found = service.activePunishment(target.get().uuid(), target.get().ip(), type);
                    c.getSource().sendSuccess(() -> Component.literal(found.map(r ->
                            target.get().name() + " is " + type + ": " + r.reason + " (#" + r.id + ")"
                    ).orElse(target.get().name() + " is not " + type + ".")), false);
                    return 1;
                })));
    }

    private static void registerDupeIp(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dupeip").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    Set<String> otherIds = new LinkedHashSet<>();
                    for (String ip : service.db().ips().getOrDefault(target.get().uuid(), List.of())) {
                        for (String id : service.db().accounts().getOrDefault(ip, List.of())) {
                            if (!id.equals(target.get().uuid())) otherIds.add(id);
                        }
                    }
                    if (otherIds.isEmpty()) {
                        c.getSource().sendSuccess(() -> Component.literal(target.get().name() + " shares no known IP."), false);
                    } else {
                        otherIds.forEach(id -> c.getSource().sendSuccess(() ->
                                Component.literal("- " + service.db().names().getOrDefault(id, id)), false));
                    }
                    return otherIds.size();
                })));
    }

    private static void registerStaffHistory(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("staffhistory").requires(s -> s.hasPermission(2))
                .then(Commands.argument("staff", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "staff");
                    PunishService service = service(c);
                    long count = service.db().records().stream().filter(r -> r.by.equalsIgnoreCase(name)).peek(r ->
                            c.getSource().sendSuccess(() -> Component.literal("#" + r.id + " [" + r.type + "] " + r.name + " - " + r.reason), false)).count();
                    return (int) count;
                })));
    }

    private static void registerPunish(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("punish").requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(c -> showOffenses(c, StringArgumentType.getString(c, "target")))
                        .then(Commands.argument("offense", StringArgumentType.word())
                                .executes(c -> showPlan(c, StringArgumentType.getString(c, "target"),
                                        StringArgumentType.getString(c, "offense"), ""))
                                .then(Commands.argument("rest", StringArgumentType.greedyString())
                                        .executes(c -> executePunish(c,
                                                StringArgumentType.getString(c, "target"),
                                                StringArgumentType.getString(c, "offense"),
                                                StringArgumentType.getString(c, "rest"))))));
        d.register(root);
    }

    private static int showOffenses(CommandContext<CommandSourceStack> c, String targetName) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetName, false);
        if (target.isEmpty()) return fail(c, "Unknown player.");

        c.getSource().sendSuccess(() -> Component.literal(target.get().name() + " - select an offense:"), false);
        String group = "";
        for (Offense offense : service.offenses()) {
            if (!group.equals(offense.group())) {
                group = offense.group();
                final String groupName = group;
                c.getSource().sendSuccess(() -> Component.literal("[" + groupName + "]"), false);
            }
            var plan = service.plan(target.get(), offense, "");
            String command = "/punish " + target.get().name() + " " + offense.id();
            Component line = Component.literal("› " + offense.name() + " » " + ordinal(plan.number()) + ": " + plan.label())
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command + " "))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to fill " + command))));
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int showPlan(CommandContext<CommandSourceStack> c, String targetName, String offenseName, String rest) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetName, false);
        Offense offense = service.findOffense(offenseName);
        if (target.isEmpty() || offense == null) return fail(c, "Unknown target or offense.");
        var plan = service.plan(target.get(), offense, CommandUtil.cleanSilent(rest));
        if (plan.error() != null) return fail(c, plan.error());
        c.getSource().sendSuccess(() -> Component.literal(
                offense.name() + " -> " + plan.label() + " (" + ordinal(plan.number())
                        + " offense, warning bonus " + DurationUtil.format(plan.bonus()) + ")"), false);
        return 1;
    }

    private static int executePunish(CommandContext<CommandSourceStack> c, String targetName, String offenseName, String rest) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetName, false);
        Offense offense = service.findOffense(offenseName);
        if (target.isEmpty() || offense == null) return fail(c, "Unknown target or offense.");

        var plan = service.plan(target.get(), offense, CommandUtil.cleanSilent(rest));
        if (plan.error() != null) return fail(c, plan.error());
        boolean silent = CommandUtil.silent(rest);
        String reason = offense.name() + " (" + ordinal(plan.number()) + " offense)"
                + (plan.note().isBlank() ? "" : " - " + plan.note());

        PunishmentRecord main = null;
        if (plan.warn()) {
            main = service.applyWarn(c.getSource().getServer(), c.getSource(), target.get(), false, reason, silent);
            main.offense = offense.id();
            main.offenseNumber = plan.number();
            main.caseId = main.id;
            main.primary = true;
        }
        if (plan.mute()) {
            PunishmentRecord mute = service.create("mute", false, target.get(), reason,
                    c.getSource().getTextName(), service.muteLength(plan));
            mute.offense = offense.id();
            mute.offenseNumber = plan.number();
            mute.silent = silent;
            if (target.get().player() != null) target.get().player().sendSystemMessage(service.muteComponent(mute));
            DiscordLogger.log(PunishConfig.load(service.root()), mute, false);
        }
        if (plan.permanent() || plan.ban() > 0) {
            long duration = plan.permanent() ? 0 : plan.ban() + plan.bonus();
            service.applyBan(c.getSource().getServer(), c.getSource(), target.get(), false, duration, reason, silent);
            PunishmentRecord ban = service.db().records().get(service.db().records().size() - 1);
            ban.offense = offense.id();
            ban.offenseNumber = plan.number();
            ban.caseId = ban.id;
            ban.primary = true;
            main = ban;
        }
        service.save();
        c.getSource().sendSuccess(() -> Component.literal("Applied " + offense.name() + " (" + ordinal(plan.number()) + "): " + plan.label()), true);
        return main == null ? 0 : 1;
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
        };
        return n + suffix;
    }

    private static void registerAlias(CommandDispatcher<CommandSourceStack> d, String alias, String command, boolean ipMode) {
        d.register(Commands.literal(alias).requires(s -> s.hasPermission(2))
                .then(Commands.argument("target", StringArgumentType.word()).executes(c -> {
                    return switch (command) {
                        case "unban" -> executeUnbanAlias(c, false);
                        case "ipban" -> executeBan(c, "ipban", false, true);
                        case "unbanip" -> executeUnbanAlias(c, true);
                        default -> 0;
                    };
                })));
    }

    private static int executeUnbanAlias(CommandContext<CommandSourceStack> c, boolean ipMode) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(),
                StringArgumentType.getString(c, "target"), ipMode);
        if (target.isEmpty()) return fail(c, "Unknown target.");
        if (ipMode) c.getSource().getServer().getPlayerList().getIpBans().remove(target.get().ip());
        else c.getSource().getServer().getPlayerList().getBans().remove(target.get().profile());

        for (PunishmentRecord r : service.db().records()) {
            if (r.active && r.type.equals("ban") && r.ipBased == ipMode
                    && Objects.equals(r.uuid, target.get().uuid()) && Objects.equals(r.ip, target.get().ip())) {
                r.active = false;
                r.removedBy = c.getSource().getTextName();
                r.removedAt = System.currentTimeMillis();
            }
        }
        service.save();
        c.getSource().sendSuccess(() -> Component.literal("Unbanned " + (ipMode ? target.get().ip() : target.get().name()) + "."), true);
        return 1;
    }

    private static String getOptional(CommandContext<CommandSourceStack> c, String key) {
        try { return StringArgumentType.getString(c, key); } catch (IllegalArgumentException ignored) { return ""; }
    }

    private static PunishService service(CommandContext<CommandSourceStack> c) {
        return PunishMod.service(c.getSource().getServer().getServerDirectory().toAbsolutePath());
    }

    private static int fail(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}