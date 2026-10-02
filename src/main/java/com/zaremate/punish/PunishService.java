package com.zaremate.punish;

import com.google.gson.*;
import com.zaremate.punish.data.Offense;
import com.zaremate.punish.data.PunishmentDatabase;
import com.zaremate.punish.data.PunishmentRecord;
import com.zaremate.punish.util.DurationUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.UserBanListEntry;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class PunishService {
    private final Path root;
    private final Path file;
    private final PunishmentDatabase db;
    private final PunishConfig config;

    private static final Set<String> MUTED_COMMANDS =
            Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm", "r", "reply");

    private static final List<Offense> OFFENSES = List.of(
            new Offense("xray", "Cheating", "X-Ray", List.of("4d", "perm"), List.of("x-ray", "cheating")),
            new Offense("hacking", "Cheating", "Duping/hacking", List.of("20d", "perm"), List.of("duping", "dupe", "hacks", "cheats", "hacker")),
            new Offense("vanilla-dupe", "Cheating", "Duping with vanilla glitches", List.of("warn", "4d", "15d"), List.of("duping", "dupe", "glitch", "vanilladupe")),
            new Offense("claim-glitch", "Cheating", "Glitching past claim protection", List.of("10d", "perm"), List.of("glitch", "raid", "claimraid", "bypass")),
            new Offense("blaze-spawner", "Claiming & Building", "Claiming/breaking/taking blaze spawners", List.of("warn", "3d", "12d"), List.of("blaze", "spawner", "spawners")),
            new Offense("claim-base", "Claiming & Building", "Claiming somebody else's base/shop", List.of("5d", "12d", "perm"), List.of("claiming", "claim", "shop")),
            new Offense("bad-build", "Claiming & Building", "Inappropriate structures", List.of("warn", "5d", "perm"), List.of("build", "building", "inappropriate", "nsfw")),
            new Offense("bad-factory", "Claiming & Building", "Factories that break the rules", List.of("warn", "5d", "12d"), List.of("factory", "factories", "lag", "laggy")),
            new Offense("anti-afk", "Claiming & Building", "Bypassing anti-AFK", List.of("warn", "4d", "7d"), List.of("afk", "afkbypass", "macro", "macroing")),
            new Offense("chunkloader", "Claiming & Building", "Vanilla chunkloaders", List.of("36h", "10d", "perm"), List.of("chunkloading", "chunkloaders", "portal")),
            new Offense("jetpack", "PvP", "Jetpack in a fight", List.of("warn", "4d", "15d"), List.of("jet", "jetpacks")),
            new Offense("base-camping", "PvP", "Base camping/spawnkilling", List.of("warn", "4d", "12d"), List.of("camping", "camp", "spawncamp")),
            new Offense("spawn-kill", "PvP", "Killing at spawn / pushing off spawn", List.of("16h", "5d", "perm"), List.of("spawnkill", "spawnkilling", "spawn")),
            new Offense("airship-kill", "PvP", "Killing through airships", List.of("2d", "10d", "perm"), List.of("airship", "ship", "shipkill")),
            new Offense("invis-kill", "PvP", "Killing with invisibility", List.of("16h", "4d", "15d"), List.of("invis", "invisibility", "invisible")),
            new Offense("griefing", "PvP", "Griefing/stealing outside claims", List.of("warn~5d", "7d", "perm"), List.of("grief", "griefer", "stealing", "steal", "raiding")),
            new Offense("toxicity", "Chat", "Excessive toxicity/vulgarity", List.of("mute+warn", "3d", "12d"), List.of("toxic", "swearing", "spam", "spamming", "chat")),
            new Offense("racism", "Chat", "Racism/homophobia", List.of("mute+2d", "10d", "perm"), List.of("racist", "slur", "slurs", "homophobia", "homophobic")),
            new Offense("disrespect", "Chat", "Staff disrespect/incompliance", List.of("24h", "5d", "perm"), List.of("staffdisrespect", "disrespectful", "incompliance")),
            new Offense("airship-grief", "Aeronautics", "Stealing/griefing airships", List.of("2d", "12d", "perm"), List.of("airshipgrief", "shipgrief", "shipsteal")),
            new Offense("sub-levels", "Aeronautics", "Unnecessary Sable sub-levels", List.of("warn", "2d", "5d"), List.of("sable", "sublevel", "sublevels", "subworld"))
    );

    public PunishService(Path serverRoot) {
        this.root = serverRoot.toAbsolutePath().normalize();
        this.file = root.resolve("config").resolve("punish.json");
        this.db = new PunishmentDatabase(file);
        this.config = PunishConfig.load(root);
    }

    public Path root() { return root; }
    public PunishmentDatabase db() { return db; }
    public static Set<String> mutedCommands() { return MUTED_COMMANDS; }

    public void load() {
        Path legacy = root.resolve("local").resolve("kubejs").resolve("punishments.json");
        db.load(legacy);
    }
    public void save() { db.save(); }

    public String staffName(net.minecraft.commands.CommandSourceStack source) {
        return source.getTextName();
    }

    public String ip(ServerPlayer player) {
        if (player == null) return null;
        java.net.SocketAddress remote = player.connection.getRemoteAddress();
        if (!(remote instanceof InetSocketAddress address) || address.getAddress() == null) return null;
        return address.getAddress().getHostAddress();
    }

    public boolean isLocalOrProxyIp(String ip) {
        if (ip == null || ip.isBlank()) return true;
        return ip.equals("127.0.0.1")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..+")
                || ip.startsWith("169.254.")
                || ip.equals("::1")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.toLowerCase(Locale.ROOT).startsWith("fc")
                || ip.toLowerCase(Locale.ROOT).startsWith("fd")
                || ip.toLowerCase(Locale.ROOT).startsWith("fe80");
    }

    public Optional<Target> resolve(MinecraftServer server, String value, boolean ipMode) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(value);
        if (!ipMode && online != null) {
            return Optional.of(Target.player(online, ip(online)));
        }

        if (value.matches("\\d{1,3}(\\.\\d{1,3}){3}") || value.contains(":")) {
            return Optional.of(Target.ip(value));
        }

        Optional<GameProfileHolder> cached = db.findCached(value);
        if (cached.isPresent()) {
            GameProfileHolder holder = cached.get();
            return Optional.of(new Target(holder.name(), holder.uuid(), holder.ip(), null, null));
        }

        var profileCache = server.getProfileCache();
        if (profileCache != null) {
            var profile = profileCache.get(value);
            if (profile.isPresent()) {
                return Optional.of(Target.profile(profile.get(), null));
            }
        }

        return Optional.empty();
    }

    public void recordJoin(ServerPlayer player) {
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        String ip = ip(player);

        db.names().put(uuid, name);
        if (!isLocalOrProxyIp(ip)) {
            db.ips().computeIfAbsent(uuid, ignored -> new ArrayList<>()).remove(ip);
            db.ips().computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(ip);
            db.accounts().computeIfAbsent(ip, ignored -> new ArrayList<>()).remove(uuid);
            db.accounts().computeIfAbsent(ip, ignored -> new ArrayList<>()).add(uuid);
        }

        for (PunishmentRecord record : db.records()) {
            if (!record.active || !record.type.equals("warn")) continue;
            if (!record.ipBased && uuid.equals(record.uuid) && !record.delivered) {
                record.delivered = true;
                player.sendSystemMessage(warnComponent(record));
            } else if (record.ipBased && ip != null && ip.equals(record.ip)
                    && !record.seenAccounts.contains(uuid)) {
                record.seenAccounts.add(uuid);
                player.sendSystemMessage(warnComponent(record));
            }
        }
        save();
    }

    public void alertSharedIp(ServerPlayer player) {
        String currentIp = ip(player);
        if (isLocalOrProxyIp(currentIp)) return;
        String uuid = player.getUUID().toString();
        List<String> linked = db.accounts().getOrDefault(currentIp, List.of());
        if (linked.stream().noneMatch(id -> !id.equals(uuid))) return;
        for (ServerPlayer staff : player.getServer().getPlayerList().getPlayers()) {
            if (staff.hasPermissions(2)) staff.sendSystemMessage(Component.literal("[?] " + player.getGameProfile().getName() + " joined from an IP shared with another account."));
        }
    }

    /**
     * Returns all currently applicable punishments for a player.
     * UUID punishments are matched by UUID; IP punishments are matched against
     * the current IP and every IP previously recorded for the UUID.
     */
    public List<PunishmentRecord> currentPunishments(String uuid, String currentIp) {
        long now = System.currentTimeMillis();
        Set<String> knownIps = new HashSet<>(db.ips().getOrDefault(uuid, List.of()));
        if (currentIp != null) knownIps.add(currentIp);

        return db.records().stream()
                .filter(r -> r.live(now))
                .filter(r -> !r.ipBased
                        ? Objects.equals(uuid, r.uuid)
                        : r.ip != null && knownIps.contains(r.ip))
                .toList();
    }

    /**
     * Creates and enforces a ban for use by another mod.
     */
    public PunishmentRecord addApiBan(MinecraftServer server, Target target, boolean ipBased,
                                      long duration, String reason, String by, boolean silent) {
        replaceActiveEquivalent("ban", ipBased, target);
        PunishmentRecord r = create("ban", ipBased, target, reason, by, duration);
        r.silent = silent;
        Date expires = r.until == 0 ? null : new Date(r.until);

        if (ipBased) {
            if (target.ip() == null || target.ip().isBlank())
                throw new IllegalArgumentException("IP is required for an IP ban");
            server.getPlayerList().getIpBans().add(
                    new IpBanListEntry(target.ip(), new Date(r.at), by, expires, reason));
            server.getPlayerList().getPlayers().stream()
                    .filter(p -> Objects.equals(ip(p), target.ip()))
                    .forEach(p -> p.connection.disconnect(
                            Component.literal("Your IP is banned from the server.")));
        } else {
            server.getPlayerList().getBans().add(
                    new UserBanListEntry(target.profile(), new Date(r.at), by, expires, reason));
            if (target.player() != null)
                target.player().connection.disconnect(Component.literal("You are banned from the server."));
        }

        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was " + (ipBased ? "IP-banned" : "banned") + " by " + by
                            + (duration > 0 ? " for " + DurationUtil.format(duration) : "")
                            + ": " + reason), false);
        }
        save();
        DiscordLogger.log(config, r, false);
        return r;
    }

    /**
     * Creates a mute for use by another mod.
     */
    public PunishmentRecord addApiMute(MinecraftServer server, Target target, boolean ipBased,
                                       long duration, String reason, String by, boolean silent) {
        replaceActiveEquivalent("mute", ipBased, target);
        PunishmentRecord r = create("mute", ipBased, target, reason, by, duration);
        r.silent = silent;

        if (target.player() != null)
            target.player().sendSystemMessage(muteComponent(r));

        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was " + (ipBased ? "IP-muted" : "muted") + " by " + by
                            + (duration > 0 ? " for " + DurationUtil.format(duration) : "")
                            + ": " + reason), false);
        }
        save();
        DiscordLogger.log(config, r, false);
        return r;
    }

    /**
     * Creates a warning for use by another mod.
     */
    public PunishmentRecord addApiWarn(MinecraftServer server, Target target, boolean ipBased,
                                       String reason, String by, boolean silent) {
        PunishmentRecord r = create("warn", ipBased, target, reason, by, 0);
        r.silent = silent;
        r.delivered = target.player() == null;

        if (target.player() != null)
            target.player().sendSystemMessage(warnComponent(r));

        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was warned by " + by + ": " + reason), false);
        }
        save();
        DiscordLogger.log(config, r, false);
        return r;
    }

    /**
     * Records and applies a kick for use by another mod.
     */
    public PunishmentRecord addApiKick(Target target, String reason, String by, boolean silent) {
        if (target.player() == null)
            throw new IllegalArgumentException("Player must be online for a kick");

        PunishmentRecord r = create("kick", false, target, reason, by, 0);
        r.active = false;
        r.silent = silent;
        target.player().connection.disconnect(Component.literal("You were kicked: " + reason));
        save();
        DiscordLogger.log(config, r, false);
        return r;
    }

    public Optional<PunishmentRecord> activeMute(String uuid, String ip) {
        long now = System.currentTimeMillis();
        return db.records().stream()
                .filter(r -> r.type.equals("mute") && r.live(now))
                .filter(r -> r.ipBased ? ip != null && ip.equals(r.ip) : uuid.equals(r.uuid))
                .findFirst();
    }

    public Optional<PunishmentRecord> activePunishment(String uuid, String ip, String type) {
        long now = System.currentTimeMillis();
        List<String> known = db.ips().getOrDefault(uuid, List.of());
        return db.records().stream()
                .filter(r -> r.type.equals(type) && r.live(now))
                .filter(r -> !r.ipBased ? uuid.equals(r.uuid) : (ip != null && ip.equals(r.ip)) || known.contains(r.ip))
                .findFirst();
    }

    public Component muteComponent(PunishmentRecord r) {
        String duration = r.until > 0 ? " for " + DurationUtil.format(r.until - System.currentTimeMillis()) : " permanently";
        return Component.literal("[!] You are muted" + duration + ". Reason: " + r.reason + " (#" + r.id + ")");
    }

    public Component warnComponent(PunishmentRecord r) {
        return Component.literal("[!] You were warned by " + r.by + ": " + r.reason + " (#" + r.id + ")");
    }

    public PunishmentRecord create(String type, boolean ipBased, Target target, String reason, String by, long duration) {
        PunishmentRecord r = new PunishmentRecord();
        r.id = db.nextId();
        r.type = type;
        r.ipBased = ipBased;
        r.uuid = target.uuid();
        r.name = target.name();
        r.ip = target.ip();
        r.reason = reason;
        r.by = by;
        r.at = System.currentTimeMillis();
        r.until = duration > 0 ? r.at + duration : 0;
        db.records().add(r);
        return r;
    }

    public void replaceActiveEquivalent(String type, boolean ipBased, Target target) {
        long now = System.currentTimeMillis();
        db.records().stream()
                .filter(r -> r.live(now) && r.type.equals(type) && r.ipBased == ipBased)
                .filter(r -> Objects.equals(r.uuid, target.uuid()) && Objects.equals(r.ip, target.ip()))
                .forEach(r -> {
                    r.active = false;
                    r.replaced = true;
                    r.replacedAt = System.currentTimeMillis();
                });
    }

    public void applyBan(MinecraftServer server, net.minecraft.commands.CommandSourceStack source,
                         Target target, boolean ipBased, long duration, String reason, boolean silent) {
        replaceActiveEquivalent("ban", ipBased, target);
        PunishmentRecord r = create("ban", ipBased, target, reason, staffName(source), duration);
        r.silent = silent;
        save();

        Date expires = r.until == 0 ? null : new Date(r.until);
        if (ipBased) {
            server.getPlayerList().getIpBans().add(new IpBanListEntry(target.ip(), new Date(r.at), r.by, expires, reason));
            server.getPlayerList().getPlayers().stream()
                    .filter(p -> Objects.equals(ip(p), target.ip()))
                    .forEach(p -> p.connection.disconnect(Component.literal("Your IP is banned from the server.")));
        } else {
            server.getPlayerList().getBans().add(new UserBanListEntry(target.profile(), new Date(r.at), r.by, expires, reason));
            if (target.player() != null) {
                target.player().connection.disconnect(Component.literal("You are banned from the server."));
            }
        }

        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was " + (ipBased ? "IP-banned" : "banned") + " by " + r.by
                            + (duration > 0 ? " for " + DurationUtil.format(duration) : "")
                            + ": " + reason), false);
        }
    }

    public void applyMute(MinecraftServer server, net.minecraft.commands.CommandSourceStack source,
                          Target target, boolean ipBased, long duration, String reason, boolean silent) {
        replaceActiveEquivalent("mute", ipBased, target);
        PunishmentRecord r = create("mute", ipBased, target, reason, staffName(source), duration);
        r.silent = silent;
        save();

        if (target.player() != null) target.player().sendSystemMessage(muteComponent(r));
        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was " + (ipBased ? "IP-muted" : "muted") + " by " + r.by
                            + (duration > 0 ? " for " + DurationUtil.format(duration) : "")
                            + ": " + reason), false);
        }
    }

    public PunishmentRecord applyWarn(MinecraftServer server, net.minecraft.commands.CommandSourceStack source,
                          Target target, boolean ipBased, String reason, boolean silent) {
        PunishmentRecord r = create("warn", ipBased, target, reason, staffName(source), 0);
        r.silent = silent;
        r.delivered = target.player() == null;
        save();

        if (target.player() != null) target.player().sendSystemMessage(warnComponent(r));
        if (!silent) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    target.name() + " was warned by " + r.by + ": " + reason), false);
        }
        DiscordLogger.log(config, r, false);
        return r;
    }

    public void applyKick(CommandSourceStack source, Target target, String reason, boolean silent) {
        if (target.player() == null) return;
        PunishmentRecord r = create("kick", false, target, reason, staffName(source), 0);
        r.active = false;
        r.silent = silent;
        save();
        target.player().connection.disconnect(Component.literal("You were kicked: " + reason));
        DiscordLogger.log(config, r, false);
    }

    public Offense findOffense(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return OFFENSES.stream().filter(o ->
                o.id().equalsIgnoreCase(q)
                        || o.name().toLowerCase(Locale.ROOT).contains(q)
                        || o.group().toLowerCase(Locale.ROOT).contains(q)
                        || o.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).startsWith(q))
        ).findFirst().orElse(null);
    }

    public List<Offense> offenses() { return OFFENSES; }

    public void lift(PunishmentRecord record, CommandSourceStack source) {
        record.active = false;
        record.removedBy = source.getTextName();
        record.removedAt = System.currentTimeMillis();
        DiscordLogger.log(config, record, true);
    }

    public int offenseCount(String uuid, String offense) {
        return (int) db.records().stream()
                .filter(r -> r.primary && r.removedBy == null && offense.equals(r.offense) && uuid != null && uuid.equals(r.uuid))
                .count();
    }

    public long warningBonus(int warnings) {
        if (warnings >= 5) return DurationUtil.parse("4d");
        if (warnings == 4) return DurationUtil.parse("2d");
        if (warnings == 3) return DurationUtil.parse("1d");
        return 0;
    }

    public int warningCount(String uuid) {
        return (int) db.records().stream()
                .filter(r -> r.type.equals("warn") && r.active && !r.ipBased && uuid.equals(r.uuid))
                .count();
    }

    public Plan plan(Target target, Offense offense, String rest) {
        int number = offenseCount(target.uuid(), offense.id()) + 1;
        String raw = offense.steps().get(Math.min(number - 1, offense.steps().size() - 1));
        Step step = Step.parse(raw);
        String[] tokens = rest == null || rest.isBlank() ? new String[0] : rest.trim().split("\\s+");
        long custom = tokens.length == 0 ? 0 : DurationUtil.parse(tokens[0]);
        String note = tokens.length > 0 && custom >= 0
                ? String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)) : String.join(" ", tokens);
        int warnings = warningCount(target.uuid());
        if (custom > 0 && step.upTo == 0)
            return new Plan(number, step, warnings, warningBonus(warnings), note, "This offense has a fixed punishment step.");

        long ban = step.ban;
        boolean warn = step.warn;
        if (custom > 0 && step.upTo > 0) {
            ban = Math.min(custom, step.upTo);
            warn = false;
        }
        long bonus = ban > 0 ? warningBonus(warnings) : 0;
        return new Plan(number, new Step(warn, step.mute, ban, step.permanent, step.upTo),
                warnings, bonus, note, null);
    }

    public long muteLength(Plan plan) {
        return (plan.ban() > 0 ? plan.ban() + plan.bonus() : 0) + DurationUtil.parse("1d");
    }

    public record Plan(int number, Step step, int warnings, long bonus, String note, String error) {
        public boolean warn() { return step.warn; }
        public boolean mute() { return step.mute; }
        public boolean permanent() { return step.permanent; }
        public long ban() { return step.ban; }
        public String label() {
            List<String> parts = new ArrayList<>();
            if (permanent()) parts.add("permanent ban");
            if (ban() > 0) parts.add(DurationUtil.format(ban() + bonus) + " ban"
                    + (bonus > 0 ? " (+" + DurationUtil.format(bonus) + " for " + warnings + " warnings)" : ""));
            if (mute()) parts.add(DurationUtil.format((ban() > 0 ? ban() + bonus : 0) + DurationUtil.parse("1d")) + " mute");
            if (warn()) parts.add("warn");
            return String.join(" + ", parts) + (step.upTo > 0 && warn() ? " (or up to " + DurationUtil.format(step.upTo) + " ban)" : "");
        }
    }

    public record Step(boolean warn, boolean mute, long ban, boolean permanent, long upTo) {
        public static Step parse(String text) {
            boolean warn = false, mute = false, permanent = false;
            long ban = 0, upTo = 0;
            for (String part : text.split("\\+")) {
                String[] bits = part.split("~", 2);
                if (bits[0].equals("warn")) warn = true;
                else if (bits[0].equals("mute")) mute = true;
                else if (bits[0].equals("perm")) permanent = true;
                else ban = Math.max(0, DurationUtil.parse(bits[0]));
                if (bits.length > 1) upTo = Math.max(0, DurationUtil.parse(bits[1]));
            }
            return new Step(warn, mute, ban, permanent, upTo);
        }
    }

    public record Target(String name, String uuid, String ip,
                         com.mojang.authlib.GameProfile profile, ServerPlayer player) {
        static Target player(ServerPlayer p, String ip) {
            return new Target(p.getGameProfile().getName(), p.getUUID().toString(), ip, p.getGameProfile(), p);
        }
        static Target ip(String ip) { return new Target(ip, null, ip, null, null); }
        static Target profile(com.mojang.authlib.GameProfile profile, String ip) {
            return new Target(profile.getName(), profile.getId().toString(), ip, profile, null);
        }
    }

    public record GameProfileHolder(String name, String uuid, String ip) {}
}