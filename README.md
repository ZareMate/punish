# Punish

A server-side NeoForge punishment system for Minecraft 1.21.1, rewritten from the original KubeJS implementation.

## Build and installation

Build with Java 21 and Gradle:

```sh
gradle build
```

The project targets Minecraft 1.21.1 and builds against NeoForge 21.1.249.
Copy `build/libs/punish-1.0.0.jar` into the server's `mods/` directory and restart.
KubeJS is not required.

## Commands

`<argument>` is required; `[argument]` is optional. Run commands with `/` in game
or without it in the server console.

| Command | Behavior |
| --- | --- |
| `/ban <player> [duration] [reason]` | Ban permanently or for a duration. |
| `/tempban <player> <duration> [reason]` | Ban for a required positive duration. |
| `/ipban <player-or-IP> [duration] [reason]` | Ban an IP permanently or temporarily. |
| `/tempipban <player-or-IP> <duration> [reason]` | Ban an IP for a required positive duration. |
| `/unban <player>` | Remove a player's ban. |
| `/unbanip <player-or-IP>` | Remove an IP ban. |
| `/mute <player> [duration] [reason]` | Mute permanently or for a duration. |
| `/tempmute <player> <duration> [reason]` | Mute for a required positive duration. |
| `/ipmute <player-or-IP> [duration] [reason]` | Mute an IP permanently or temporarily. |
| `/unmute <player>` | Remove active player and matching IP mutes. |
| `/warn <player> [reason]` | Add a player warning. |
| `/ipwarn <player-or-IP> [reason]` | Add an IP warning. |
| `/warn_remove <player> [warningId]` | Remove the latest active player warning or a specific warning. |
| `/unwarn <player> [warningId]` | Same behavior as `warn_remove`, with its own permission. |
| `/warnings <player>` | List active player warnings and their IDs. |
| `/kick <player> [reason]` | Disconnect an online player. |
| `/history <player>` | Show punishment history, including matching IP records. |
| `/checkban <player>` | Check the player's current ban. |
| `/checkmute <player>` | Check the player's current mute. |
| `/dupeip <player>` | Show accounts associated with the player's recorded IPs. |
| `/staffhistory <staff>` | Show punishments issued by a staff name. |
| `/punish <player>` | Show the clickable offense menu. |
| `/punish <player> <offense>` | Preview the next escalation step. |
| `/punish <player> <offense> <note-or-duration-and-note>` | Apply the next escalation step. |
| `/unoffense <player> <caseId>` | Remove an offense case and its related punishment records. |
| `/pardon <player>` | Alias for `unban`. |
| `/ban-ip <player-or-IP>` | Apply a permanent IP ban. |
| `/pardon-ip <player-or-IP>` | Alias for `unbanip`. |

Player-name completion is case-insensitive and includes online players and offline
names saved by Punish, with duplicates removed. It is available across player-target
commands, aliases, and `staffhistory`. Offline suggestions survive server restarts;
names known only to Minecraft's profile cache are not included in suggestions.
`punish` also suggests offense names and aliases, and `unoffense` suggests case IDs.

Warning removal preserves history and records the moderator and removal time. It
only removes active, UUID-based warnings belonging to the selected player; it does
not remove IP warnings or undo an entire offense case. Use `unoffense` for a case.

```text
/warn Alex Please follow the chat rules
/warnings Alex
/warn_remove Alex 42
/warn_remove Alex
/tempban Alex 1d12h Repeated rule violation -s
/punish Alex xray
/punish Alex xray Confirmed by staff review
```

Append `-s` to a punishment's reason or offense arguments for a silent announcement.
A bare `/punish <player> <offense>` only previews; supply a note (or `-s`) to apply it.
Custom durations are accepted only for offense steps with an optional ban and are
capped at that step's limit. Offense definitions are currently built into
`PunishService`; they are not settings in `punish-config.json`.

Mutes block chat and the messaging commands `msg`, `tell`, `w`, `me`, `say`,
`teammsg`, `tm`, `r`, and `reply`. IP punishment commands reject missing, local,
private, and link-local target addresses.

## Permissions

Commands use NeoForge permission nodes, which LuckPerms can manage when installed
as the active NeoForge permission handler. LuckPerms is optional; without a
permission provider, all nodes default to operator level 4. With LuckPerms,
explicit grants allow non-operators and explicit denials override operator access.
Unset nodes retain the operator-level-4 default. Console and command blocks require
permission level 4.

Normal, temporary, and undo variants share one permission per command family.
IP punishment commands retain their own permissions.

| Command | Permission |
| --- | --- |
| `/ban`, `/tempban`, `/unban` | `punish.ban` |
| `/ipban`, `/tempipban`, `/unbanip` | `punish.ipban` |
| `/mute`, `/tempmute`, `/unmute` | `punish.mute` |
| `/ipmute` | `punish.ipmute` |
| `/warn`, `/unwarn`, `/warn_remove` | `punish.warn` |
| `/ipwarn` | `punish.ipwarn` |
| `/warnings` | `punish.warnings` |
| `/kick` | `punish.kick` |
| `/history`, `/staffhistory` | `punish.history`, `punish.staffhistory` respectively |
| `/checkban`, `/checkmute` | `punish.checkban`, `punish.checkmute` respectively |
| `/dupeip` | `punish.dupeip` |
| `/punish`, `/unoffense` | `punish.punish` |

Existing grants or denials on separate temporary/undo nodes must be moved to the
normal family node above; the old nodes are no longer checked. Granting a family
node allows all listed variants, including permanent punishments and removal.

`punish.punish` authorizes the complete configured escalation, including its bans,
mutes, and warnings; it does not require the individual manual-command nodes.

| Alias | Permission |
| --- | --- |
| `/pardon` | `punish.ban` |
| `/ban-ip` | `punish.ipban` |
| `/pardon-ip` | `punish.ipban` |

| Notification | Permission |
| --- | --- |
| Receive silent punishment announcements | `punish.notify.silent` |
| Receive shared-IP/alt login alerts | `punish.alerts.alts` |

Alt login alerts also require `altAlertsEnabled: true` in
`config/punish-config.json`. Setting it to `false` disables those alerts for
everyone, regardless of permissions. `/dupeip` remains available to players with
`punish.dupeip`.

Examples (run in the server console or prefix with `/` in game):

```text
lp group moderator permission set punish.mute true
lp group moderator permission set punish.warn true
lp group moderator permission set punish.notify.silent true
lp group moderator permission set punish.alerts.alts true
lp group admin permission set punish.* true
lp user PlayerName permission set punish.ban false
```

Using `-s` remains available to anyone allowed to run the corresponding command;
`punish.notify.silent` controls recipients. After changing permissions, reconnect
if the client's command suggestions have not refreshed. If LuckPerms permissions
are ignored, check that NeoForge's `permissionHandler` is set to
`luckperms:permission_handler` in the world's `serverconfig/neoforge-server.toml`
and restart the server.

## Configuration and storage

Paths are relative to the server directory:

| File | Contents |
| --- | --- |
| `config/punish.json` | Punishment records, IDs, saved player names, IP history, and account associations. |
| `config/punish-config.json` | Alt alerts, appeal link, and optional Discord logging settings; created with defaults when absent. |

| Setting | Purpose |
| --- | --- |
| `altAlertsEnabled` | Enable automatic shared-IP login alerts; defaults to `true`. Set to `false` to disable alerts. IP history and `/dupeip` remain available. |
| `appealDiscord` | Discord invite displayed in appeal messages. |
| `discordEnabled` | Enable webhook logging; defaults to `false`. |
| `serverName` | Webhook display name. |
| `warnWebhook` | Warning logs, including IP warnings. |
| `muteWebhook` | Mute logs, including IP mutes. |
| `banWebhook` | Player ban and kick logs. |
| `ipWebhook` | IP ban logs. |

Webhook URLs default to empty strings; a missing URL skips that category.
Restart after configuration edits so all settings are reloaded.

## Duration format

Use `s`, `m`, `h`, `d`, `w`, `mo`, and `y`, for example `30m`, `1h`, `3d`, or
`1d12h`. Months are 30 days and years are 365 days. Temporary commands require a
positive duration; ordinary ban and mute commands default to permanent when no
duration is supplied.

## Migration

When `config/punish.json` does not exist, Punish loads the legacy
`local/kubejs/punishments.json` and saves it to the new location. An existing new
database takes precedence, so the legacy file is not merged on later starts.
The original legacy file is left in place.

Back up the database before migration and disable the old punishment script when
switching to the mod. The legacy script and its webhook credentials are not
included; configure webhook URLs separately in `config/punish-config.json`.

## Java API

Other server-side mods can depend on Punish and use:

    import com.zaremate.punish.api.PunishApi;
    import com.zaremate.punish.api.Punishment;
    import com.zaremate.punish.api.PunishmentType;
    import com.zaremate.punish.data.Offense;
    import java.util.List;

    List<Punishment> current = PunishApi.getCurrentPunishments(server, playerUuid);

For the player's recorded offense cases (history):

    List<Punishment> offenses = PunishApi.getRecordedOffenses(server, playerUuid);

To get every configured offense available to `/punish`:

    List<Offense> offenses = PunishApi.getOffenses(server);

Each `Offense` exposes:

    offense.id()
    offense.group()
    offense.name()
    offense.steps()
    offense.aliases()

For example, the `xray` offense describes the configured X-Ray escalation steps and aliases.

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

Use `durationMillis = 0` for a permanent ban or mute. Durations are ignored for
warnings and kicks. Negative durations are rejected; IP punishments require a
nonblank IP, and kicks cannot be IP-based.

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

`getCurrentPunishments(...)` includes active UUID punishments and active IP
punishments that apply to the player's current or known IP addresses.
`getCurrentPunishment(server, playerUuid, PunishmentType.MUTE)` returns an
`Optional<Punishment>` for a single matching type.

To apply an offense with the same escalation logic used by `/punish`:

```java
List<Punishment> created = PunishApi.addPunishment(
    server, playerUuid, playerName, "xray", "MyMod"
);

List<Punishment> silentCase = PunishApi.addPunishment(
    server, playerUuid, playerName,
    "griefing", "3d staff review", "MyMod", true
);
```

Offense calls return every punishment created for the case, including combined
steps such as mute plus warning. Unknown offenses and invalid duration overrides
throw `IllegalArgumentException`. The final escalation step repeats after the
configured steps are exhausted.

Convenience overloads accept a `ServerPlayer` for current-punishment queries,
manual punishments, and offense application. Query results are unmodifiable lists
of immutable `Punishment` snapshots. Recorded offense history is oldest first and
returns only the primary record of each case, including removed or expired cases.
`Punishment` exposes record details and `isPermanent()`, `isExpired()`, and
`isCurrent()` helpers. `isPermanent()` means the record has no expiry timestamp;
it does not mean a kick is an ongoing restriction.

The public API currently provides queries and creation, not removal methods.
Call it from server-side code on the server thread; API creation does not perform
command permission checks for the caller.

## Tests

Run `gradle test` or `gradle build` (CI runs the tests as part of the build).
The HTML report is generated at `build/reports/tests/test/index.html`.

| Suite | Behavior covered |
| --- | --- |
| `CommandsTest` | All command roots and aliases, creation/removal, previews, reporting, invalid input, online/offline suggestions across all commands, targeted warning removal, silent flags, kicks |
| `PermissionsTest` | Every command's registered node, alias mapping, non-op grants, op denials, defaults, independent notification recipients |
| `LifecycleTest` | UUID/IP matching, replacement, expiry, bans/disconnects, warning delivery, joins, kicks, offense removal, audit data, address protection |
| `EscalationTest` | Every configured offense through every step and repeated final step, combined cases, warning bonuses, caps, rejected overrides |
| `ResolutionTest` | Online players, cached players, latest addresses, vanilla profile fallback, unknown targets, IP-only targeting |
| `ApiTest` | All punishment types, public queries, immutable results, validation, offense history/escalation, expiry |
| `EventsTest` | Login handling, chat mutes, blocked messaging commands including vanilla namespaced aliases, expiry, console exemptions |
| `StorageTest` | JSON round trips, IDs, restart persistence, one-time legacy migration, config defaults/custom values/fallback |
| `UtilityTest` | Duration units, formatting, malformed input/overflow, silent flag handling |
| `DiscordLoggerTest` | Webhook routing, payloads, lifted records, IP masking, disabled/missing/invalid endpoints |
| `MessageFormattingTest` | Message text, styles, warning counts, disconnect screens, appeal links, history, literal user input |

Tests use temporary directories, UTC dates, Mockito server/player boundaries, real
Brigadier command dispatch, real vanilla ban-list files, and an HTTP server bound
to loopback for webhook tests. They never contact Discord or use production data.
The fixture bootstraps vanilla registries and bypasses mod feature-flag discovery,
which is unavailable outside a NeoForge launch.

The permission tests substitute NeoForge's permission-provider boundary; they do
not run LuckPerms itself. A dedicated-server smoke test is still needed for actual
LuckPerms provider selection/context changes, NeoForge event-bus wiring, network
login/disconnect behavior, and client command-tree refresh. In-game visual layout
and live Discord delivery also remain integration checks. Passing these tests does
not imply complete line/branch coverage or a verified production server startup.
