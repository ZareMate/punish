package com.zaremate.punish.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zaremate.punish.PunishMod;
import com.zaremate.punish.PunishPermissions;
import com.zaremate.punish.PunishConfig;
import com.zaremate.punish.DiscordLogger;
import com.zaremate.punish.PunishService;
import com.zaremate.punish.data.Offense;
import com.zaremate.punish.data.PunishmentRecord;
import com.zaremate.punish.util.CommandUtil;
import static com.zaremate.punish.util.ChatText.*;
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
        registerUnwarn(d, "unwarn");
        registerUnwarn(d, "warn_remove");
        registerWarnings(d);
        registerKick(d);
        registerHistory(d);
        registerCheck(d, "checkban", "ban");
        registerCheck(d, "checkmute", "mute");
        registerDupeIp(d);
        registerStaffHistory(d);
        registerPunish(d);
        registerUnoffense(d);

        registerAlias(d, "pardon", "unban", false);
        registerAlias(d, "ban-ip", "ipban", true);
        registerAlias(d, "pardon-ip", "unbanip", true);
    }

    private static void registerBan(CommandDispatcher<CommandSourceStack> d, String name, boolean durationRequired, boolean ipMode) {
        d.register(Commands.literal(name).requires(s -> PunishPermissions.command(s, name))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
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
        d.register(Commands.literal(name).requires(s -> PunishPermissions.command(s, name))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> opt = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), ipMode);
                    if (opt.isEmpty()) return fail(c, "Unknown player or IP.");
                    PunishService.Target target = opt.get();
                    if (ipMode) {
                        c.getSource().getServer().getPlayerList().getIpBans().remove(target.ip());
                    } else {
                        // UserBanList is keyed by UUID. Always provide a real profile,
                        // including when the target was resolved from the Punish database.
                        com.mojang.authlib.GameProfile profile = target.profile();
                        if (profile == null && target.uuid() != null) {
                            try {
                                profile = new com.mojang.authlib.GameProfile(
                                        UUID.fromString(target.uuid()),
                                        target.name()
                                );
                            } catch (IllegalArgumentException ignored) {
                                profile = null;
                            }
                        }
                        if (profile != null) {
                            c.getSource().getServer().getPlayerList().getBans().remove(profile);
                        }
                    }

                    int ended = 0;
                    for (PunishmentRecord r : service.db().records()) {
                        if (!r.active || !r.type.equals("ban") || r.ipBased != ipMode) continue;

                        boolean matches = ipMode
                                ? Objects.equals(r.ip, target.ip())
                                : Objects.equals(r.uuid, target.uuid());

                        if (matches) {
                            r.active = false;
                            r.removedBy = c.getSource().getTextName();
                            r.removedAt = System.currentTimeMillis();
                            ended++;
                        }
                    }
                    service.save();
                    c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &7was &a{1} &7by &f{2}", target.name(), ipMode ? "IP-unbanned" : "unbanned", service.staffName(c.getSource())), true);
                    return ended > 0 ? 1 : 0;
                })));
    }

    private static void registerMute(CommandDispatcher<CommandSourceStack> d, String name, boolean durationRequired, boolean ipMode) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(s -> PunishPermissions.command(s, name));
        root.then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
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
        d.register(Commands.literal("unmute").requires(s -> PunishPermissions.command(s, "unmute"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
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
                    if (ended == 0) return fail(c, target.get().name() + " is not muted.");
                    c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &7was &aunmuted &7by &f{1}",
                            target.get().name(), service.staffName(c.getSource())), true);
                    if (target.get().player() != null) target.get().player().sendSystemMessage(format(GOOD + "&7You are no longer muted."));
                    return ended;
                })));
    }

    private static void registerWarn(CommandDispatcher<CommandSourceStack> d, String name, boolean ipMode) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(s -> PunishPermissions.command(s, name));
        root.then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
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

    private static void registerUnwarn(CommandDispatcher<CommandSourceStack> d, String name) {
        d.register(Commands.literal(name).requires(s -> PunishPermissions.command(s, name))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
                        .executes(c -> executeUnwarn(c, 0))
                        .then(Commands.argument("warningId", LongArgumentType.longArg(1))
                                .executes(c -> executeUnwarn(c, LongArgumentType.getLong(c, "warningId"))))));
    }

    private static int executeUnwarn(CommandContext<CommandSourceStack> c, long warningId) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(),
                StringArgumentType.getString(c, "target"), false);
        if (target.isEmpty()) return fail(c, "Unknown player.");
        PunishmentRecord warning = service.db().records().stream()
                .filter(r -> r.live(System.currentTimeMillis()) && r.type.equals("warn") && !r.ipBased)
                .filter(r -> Objects.equals(r.uuid, target.get().uuid()))
                .filter(r -> warningId == 0 || r.id == warningId)
                .max(Comparator.comparingLong((PunishmentRecord r) -> r.at).thenComparingLong(r -> r.id))
                .orElse(null);
        if (warning == null) return fail(c, warningId == 0 ? "Player has no active warning."
                : "No active warning #" + warningId + " was found for " + target.get().name() + ".");
        warning.active = false;
        warning.removedBy = c.getSource().getTextName();
        warning.removedAt = System.currentTimeMillis();
        service.save();
        c.getSource().sendSuccess(() -> format(GOOD + "&7Removed warning &f#{0} &7from &f{1}&7 ({2} left).",
                warning.id, target.get().name(), service.warningCount(target.get().uuid())), false);
        return 1;
    }

    private static void registerWarnings(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warnings").requires(s -> PunishPermissions.command(s, "warnings"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    return showRecords(c, "Warnings of " + target.get().name(), service.db().records().stream()
                            .filter(r -> r.type.equals("warn") && r.active && !r.ipBased
                                    && Objects.equals(r.uuid, target.get().uuid())).toList());
                })));
    }

    private static void registerKick(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kick").requires(s -> PunishPermissions.command(s, "kick"));
        root.then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
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
        d.register(Commands.literal("history").requires(s -> PunishPermissions.command(s, "history"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    List<PunishmentRecord> list = service.db().records().stream()
                            .filter(r -> Objects.equals(r.uuid, target.get().uuid())
                                    || (r.ipBased && target.get().ip() != null && target.get().ip().equals(r.ip)))
                            .toList();
                    return showRecords(c, "History of " + target.get().name(), list);
                })));
    }

    private static void registerCheck(CommandDispatcher<CommandSourceStack> d, String command, String type) {
        d.register(Commands.literal(command).requires(s -> PunishPermissions.command(s, command))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
                    PunishService service = service(c);
                    Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), StringArgumentType.getString(c, "target"), false);
                    if (target.isEmpty()) return fail(c, "Unknown player.");
                    Optional<PunishmentRecord> found = service.activePunishment(target.get().uuid(), target.get().ip(), type);
                    String word = type.equals("ban") ? "banned" : "muted";
                    c.getSource().sendSuccess(() -> format((found.isPresent() ? BAD : GOOD)
                            + "&f{0} &7is {1}{2}{3}", target.get().name(), found.isPresent() ? "" : "not ",
                            word, found.isPresent() ? ":" : "."), false);
                    found.ifPresent(r -> c.getSource().sendSuccess(() -> service.recordComponent(r).copy()
                            .append(r.until > 0 ? format(" &8(ends in {0})", DurationUtil.format(r.until - System.currentTimeMillis()))
                                    : Component.empty()), false));
                    return 1;
                })));
    }

    private static void registerDupeIp(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dupeip").requires(s -> PunishPermissions.command(s, "dupeip"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
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
                        c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &7shares no IP with anyone.", target.get().name()), false);
                    } else {
                        c.getSource().sendSuccess(() -> format(BAD + "&f{0} &7shares an IP with:", target.get().name()), false);
                        otherIds.forEach(id -> c.getSource().sendSuccess(() ->
                                format("&8- ").append(service.accountComponent(id, false)), false));
                    }
                    return otherIds.size();
                })));
    }

    private static void registerStaffHistory(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("staffhistory").requires(s -> PunishPermissions.command(s, "staffhistory"))
                .then(Commands.argument("staff", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
                    String name = StringArgumentType.getString(c, "staff");
                    PunishService service = service(c);
                    return showRecords(c, "Punishments by " + name, service.db().records().stream()
                            .filter(r -> r.by.equalsIgnoreCase(name)).toList());
                })));
    }

    private static void registerPunish(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("punish").requires(s -> PunishPermissions.command(s, "punish"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
                        .executes(c -> showOffenses(c, StringArgumentType.getString(c, "target")))
                        .then(Commands.argument("offense", StringArgumentType.word())
                                .suggests(PunishCommands::suggestOffenses)
                                .executes(c -> showPlan(c, StringArgumentType.getString(c, "target"),
                                        StringArgumentType.getString(c, "offense"), ""))
                                .then(Commands.argument("rest", StringArgumentType.greedyString())
                                        .executes(c -> executePunish(c,
                                                StringArgumentType.getString(c, "target"),
                                                StringArgumentType.getString(c, "offense"),
                                                StringArgumentType.getString(c, "rest"))))));
        d.register(root);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
            names.add(player.getGameProfile().getName());
        }
        service(context).db().names().values().stream()
                .filter(Objects::nonNull).filter(name -> !name.isBlank()).forEach(names::add);
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOffenses(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        PunishService service = service(context);
        for (Offense offense : service.offenses()) {
            if (offense.id().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(offense.id());
            }
            for (String alias : offense.aliases()) {
                if (alias.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(alias);
                }
            }
        }
        return builder.buildFuture();
    }

    private static void registerUnoffense(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("unoffense").requires(s -> PunishPermissions.command(s, "unoffense"))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers)
                        .then(Commands.argument("caseId", LongArgumentType.longArg(1))
                                .suggests(PunishCommands::suggestCaseIds)
                                .executes(c -> executeUnoffense(
                                        c,
                                        StringArgumentType.getString(c, "target"),
                                        LongArgumentType.getLong(c, "caseId"))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCaseIds(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String targetName = StringArgumentType.getString(context, "target");
        Optional<PunishService.Target> target = service(context).resolve(
                context.getSource().getServer(), targetName, false);
        if (target.isEmpty() || target.get().uuid() == null) {
            return builder.buildFuture();
        }

        String remaining = builder.getRemaining();
        PunishService service = service(context);
        service.db().records().stream()
                .filter(r -> r.primary && r.caseId > 0)
                .filter(r -> Objects.equals(r.uuid, target.get().uuid()))
                .sorted(Comparator.comparingLong(r -> r.caseId))
                .forEach(r -> {
                    String id = Long.toString(r.caseId);
                    if (id.startsWith(remaining)) {
                        builder.suggest(id, format("&f{0} &8({1} offense)", r.offense, ordinal(r.offenseNumber)));
                    }
                });
        return builder.buildFuture();
    }

    private static int executeUnoffense(
            CommandContext<CommandSourceStack> c,
            String targetName,
            long caseId
    ) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(
                c.getSource().getServer(), targetName, false);
        if (target.isEmpty()) return fail(c, "Unknown player.");

        int removed = service.removeOffense(
                c.getSource().getServer(),
                target.get(),
                caseId,
                c.getSource());

        if (removed == 0) {
            return fail(c, "No offense case #" + caseId + " was found for " + target.get().name() + ".");
        }

        c.getSource().sendSuccess(() -> format(GOOD + "&7Removed offense case &f#{0} &7for &f{1} &8({2} related punishment record(s)).",
                caseId, target.get().name(), removed), true);
        return removed;
    }

    private static int showOffenses(CommandContext<CommandSourceStack> c, String targetName) {
        PunishService service = service(c);
        Optional<PunishService.Target> target = service.resolve(c.getSource().getServer(), targetName, false);
        if (target.isEmpty()) return fail(c, "Unknown player.");

        c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &7has &f{1} &7warnings{2}. Click an offense:", target.get().name(), service.warningCount(target.get().uuid()), service.warningBonus(service.warningCount(target.get().uuid())) > 0 ? " (bans get +" + DurationUtil.format(service.warningBonus(service.warningCount(target.get().uuid()))) + ")" : ""), false);
        String group = "";
        for (Offense offense : service.offenses()) {
            if (!group.equals(offense.group())) {
                group = offense.group();
                final String groupName = group;
                c.getSource().sendSuccess(() -> format("&c&l{0}", groupName.toUpperCase(Locale.ROOT)), false);
            }
            var plan = service.plan(target.get(), offense, "");
            String command = "/punish " + target.get().name() + " " + offense.id();
            Component line = format("&8› &f{0} &8» &7{1}: &f{2}", offense.name(), ordinal(plan.number()), plan.label())
                    .setStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command + " "))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, format("&7Click to fill in &f{0}", command))));
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
        c.getSource().sendSuccess(() -> format(GOOD + "&f{0}&7: {1} &f{2} &7offense » &f{3} &8(warning bonus {4})",
                target.get().name(), ordinal(plan.number()), offense.name(), plan.label(), DurationUtil.format(plan.bonus())), false);
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
        List<PunishmentRecord> caseRecords = new ArrayList<>();

        if (plan.warn()) {
            main = service.applyWarn(c.getSource().getServer(), c.getSource(), target.get(), false, reason, silent);
            main.offense = offense.id();
            main.offenseNumber = plan.number();
            caseRecords.add(main);
        }
        if (plan.mute()) {
            PunishmentRecord mute = service.create("mute", false, target.get(), reason,
                    c.getSource().getTextName(), service.muteLength(plan));
            mute.offense = offense.id();
            mute.offenseNumber = plan.number();
            mute.silent = silent;
            caseRecords.add(mute);
            if (target.get().player() != null) target.get().player().sendSystemMessage(service.muteComponent(mute));
            service.announce(c.getSource().getServer(), mute);
            DiscordLogger.log(PunishConfig.load(service.root()), mute, false);
        }
        if (plan.permanent() || plan.ban() > 0) {
            long duration = plan.permanent() ? 0 : plan.ban() + plan.bonus();
            service.applyBan(c.getSource().getServer(), c.getSource(), target.get(), false, duration, reason, silent);
            PunishmentRecord ban = service.db().records().get(service.db().records().size() - 1);
            ban.offense = offense.id();
            ban.offenseNumber = plan.number();
            main = ban;
            caseRecords.add(ban);
        }

        if (!caseRecords.isEmpty()) {
            long caseId = caseRecords.get(0).id;
            PunishmentRecord primary = caseRecords.get(0);
            for (PunishmentRecord record : caseRecords) {
                record.caseId = caseId;
                record.primary = record == primary;
            }
        }

        service.save();
        c.getSource().sendSuccess(() -> format(GOOD + "&f{0}&7: {1} &f{2} &7offense » &f{3}", target.get().name(), ordinal(plan.number()), offense.name(), plan.label()), true);
        return main == null ? 0 : 1;
    }

    private static int showRecords(CommandContext<CommandSourceStack> c, String title, List<PunishmentRecord> records) {
        c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &8({1})", title, records.size()), false);
        for (int i = records.size() - 1; i >= Math.max(0, records.size() - 10); i--) {
            PunishmentRecord record = records.get(i);
            c.getSource().sendSuccess(() -> service(c).recordComponent(record), false);
        }
        if (records.size() > 10)
            c.getSource().sendSuccess(() -> format("&8...and {0} older", records.size() - 10), false);
        return records.size();
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
        };
        return n + suffix;
    }

    private static void registerAlias(CommandDispatcher<CommandSourceStack> d, String alias, String command, boolean ipMode) {
        d.register(Commands.literal(alias).requires(s -> PunishPermissions.command(s, command))
                .then(Commands.argument("target", StringArgumentType.word()).suggests(PunishCommands::suggestPlayers).executes(c -> {
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
        c.getSource().sendSuccess(() -> format(GOOD + "&f{0} &7was &a{1} &7by &f{2}", target.get().name(), ipMode ? "IP-unbanned" : "unbanned", service.staffName(c.getSource())), true);
        return 1;
    }

    private static String getOptional(CommandContext<CommandSourceStack> c, String key) {
        try { return StringArgumentType.getString(c, key); } catch (IllegalArgumentException ignored) { return ""; }
    }

    private static PunishService service(CommandContext<CommandSourceStack> c) {
        return PunishMod.service(c.getSource().getServer().getServerDirectory().toAbsolutePath());
    }

    private static int fail(CommandContext<CommandSourceStack> c, String message) {
        c.getSource().sendSystemMessage(format(BAD + "&7{0}", message));
        return 0;
    }
}