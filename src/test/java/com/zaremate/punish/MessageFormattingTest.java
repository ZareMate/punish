package com.zaremate.punish;

import com.zaremate.punish.data.PunishmentRecord;
import com.zaremate.punish.util.ChatText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessageFormattingTest {
    @TempDir Path root;
    PunishService service;
    PunishmentRecord record;

    @BeforeEach
    void setup() {
        service = new PunishService(root);
        record = new PunishmentRecord();
        record.id = 4;
        record.type = "warn";
        record.uuid = "player-id";
        record.name = "Alex";
        record.by = "Moderator";
        record.reason = "Griefing (1st offense) - test";
        record.at = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli();
        service.db().records().add(record);
    }

    @Test
    void warningMatchesScriptAndCountsOnlyActivePlayerWarnings() {
        PunishmentRecord old = new PunishmentRecord();
        old.type = "warn";
        old.uuid = record.uuid;
        old.active = false;
        service.db().records().add(old);
        Component message = service.warnComponent(record);
        assertEquals("[!] You were warned by Moderator: Griefing (1st offense) - test (warning 1)", message.getString());
        assertStyle(message, "[", 0x8B1E1E, false);
        assertStyle(message, "!", 0xFF4343, true);
        assertStyle(message, "You were warned by ", 0xAAAAAA, false);
        assertStyle(message, "Moderator", 0xFFFFFF, false);
        assertStyle(message, " (warning 1)", 0x555555, false);
        record.ipBased = true;
        assertFalse(service.warnComponent(record).getString().contains("warning 1"));
    }

    @ParameterizedTest
    @CsvSource({"ban,banned,16733525", "mute,muted,16755200", "warn,warned,16777045", "kick,kicked,16733525"})
    void announcementsMatchScript(String type, String verb, int color) {
        record.type = type;
        Component message = service.announcement(record);
        assertEquals("[!] Alex was " + verb + " by Moderator: " + record.reason, message.getString());
        assertStyle(message, verb, color, false);
        record.ipBased = true;
        record.silent = true;
        message = service.announcement(record);
        assertEquals("[S] Alex was IP-" + verb + " by Moderator: " + record.reason, message.getString());
        assertStyle(message, "S", 0xAAAAAA, true);
        assertStyle(message, "Alex", 0xFFFFFF, false);
    }

    @Test
    void timedAnnouncementUsesOriginalDuration() {
        record.type = "ban";
        record.until = record.at + 3_600_000;
        assertEquals("[!] Alex was banned by Moderator for 1h: " + record.reason,
                service.announcement(record).getString());
    }

    @Test
    void muteShowsPermanentOrRemainingTimeAndCase() {
        record.type = "mute";
        Component permanent = service.muteComponent(record);
        assertEquals("[!] You are muted permanently. Reason: " + record.reason + " (#4)", permanent.getString());
        assertStyle(permanent, "permanently", 0xFFFFFF, false);
        assertStyle(permanent, " (#4)", 0x555555, false);
        record.until = System.currentTimeMillis() - 1;
        assertEquals("[!] You are muted for 0s. Reason: " + record.reason + " (#4)", service.muteComponent(record).getString());
    }

    @Test
    void disconnectScreensIncludeTitleAndAppeal() {
        record.type = "ban";
        Component message = service.screen(record, "You are banned from CreateCraft");
        assertEquals("CREATECRAFT\n\nYou are banned from CreateCraft\n\nReason: " + record.reason
                + "\nBy: Moderator\nDuration: Permanent\nCase: #4\n\nAppeal in our Discord: discord.gg/m64gHWxhW7"
                + "\nOpen a ticket and pick \"Other Support\"", message.getString());
        assertStyle(message, "CREATECRAFT", new int[]{0x4498DB, 0x53A0DE, 0x63A9E1, 0x72B1E3, 0x82BAE6,
                0x91C2E9, 0x82BAE6, 0x72B1E3, 0x63A9E1, 0x53A0DE, 0x4498DB}, true);
        assertStyle(message, "You are banned from CreateCraft", 0xFF4343, true);
        assertStyle(message, "Reason: ", 0xAAAAAA, false);
        record.until = 3_600_000;
        assertTrue(service.screen(record, "Banned").getString().contains("Expires: in 0s (01.01.1970 01:00)"));
        assertFalse(service.joinNote(record).contains("Permanent"));
        record.type = "kick";
        assertFalse(service.screen(record, "Kicked").getString().contains("Expires:"));
        assertFalse(service.screen(record, "Kicked").getString().contains("Duration:"));
    }

    @ParameterizedTest
    @CsvSource({"true,false,0,[active],5635925", "false,false,0,[removed],5592405",
            "false,true,0,[replaced],5592405", "true,false,1,[expired],5592405"})
    void historyShowsStatusAndColors(boolean active, boolean replaced, long until, String state, int color) {
        record.active = active;
        record.replaced = replaced;
        record.until = until;
        Component message = service.recordComponent(record);
        assertEquals("#4 WARN by Moderator 16.10.2026 - " + record.reason + " " + state, message.getString());
        assertStyle(message, "#4", 0x555555, false);
        assertStyle(message, "WARN", 0xFF5555, false);
        assertStyle(message, state, color, false);
    }

    @Test
    void templateValuesCannotInjectFormattingOrPlaceholders() {
        String reason = "&cRed <#123456> {1} $\\ and newline\ntext";
        Component message = ChatText.format(ChatText.BAD + "&7Reason: &f{0} &8end", reason);
        assertEquals("[!] Reason: " + reason + " end", message.getString());
        assertStyle(message, reason, 0xFFFFFF, false);
        assertStyle(message, "end", 0x555555, false);
    }

    @Test
    void colorCodesAndResetClearBold() {
        Component message = ChatText.format("&c&lA&fB<#123456>&lC<#ABCDEF>D&rE&L F&R G");
        assertEquals("ABCDE F G", message.getString());
        assertStyle(message, "A", 0xFF5555, true);
        assertStyle(message, "B", 0xFFFFFF, false);
        assertStyle(message, "C", 0x123456, true);
        assertStyle(message, "D", 0xABCDEF, false);
        assertStyle(message, "E", 0xFFFFFF, false);
        assertStyle(message, "F", 0xFFFFFF, true);
        assertStyle(message, "G", 0xFFFFFF, false);
    }

    @Test
    void successNoteAndSilentPrefixesMatchScript() {
        String[] prefixes = {ChatText.GOOD, ChatText.NOTE, ChatText.SILENT};
        String[] symbols = {"!", "?", "S"};
        int[] borders = {0x479F2A, 0x8B6A1E, 0x555555};
        int[] accents = {0x72FF43, 0xFFC043, 0xAAAAAA};
        for (int i = 0; i < prefixes.length; i++) {
            Component message = ChatText.format(prefixes[i] + "&7Body");
            assertEquals("[" + symbols[i] + "] Body", message.getString());
            assertStyle(message, "[", borders[i], false);
            assertStyle(message, symbols[i], accents[i], true);
            assertStyle(message, "] ", borders[i], false);
            assertStyle(message, "Body", 0xAAAAAA, false);
        }
    }

    @Test
    void kickHistoryHasNoActiveStatusAndBanHistoryShowsDuration() {
        record.type = "kick";
        assertEquals("#4 KICK by Moderator 16.10.2026 - " + record.reason,
                service.recordComponent(record).getString());
        record.type = "ban";
        record.ipBased = true;
        assertTrue(service.recordComponent(record).getString().startsWith("#4 IPBAN perm by Moderator"));
        record.until = record.at + 3_600_000;
        assertTrue(service.recordComponent(record).getString().startsWith("#4 IPBAN 1h by Moderator"));
    }

    @Test
    void configuredAppealLinkAppearsInScreenAndRejoinNote() throws Exception {
        java.nio.file.Files.writeString(root.resolve("config/punish-config.json"),
                "{\"appealDiscord\":\"discord.gg/example\"}");
        service = new PunishService(root);
        record.type = "ban";
        assertTrue(service.screen(record, "Banned").getString().contains("discord.gg/example"));
        assertEquals("§f" + record.reason + "\n\n§7By §fModerator §8| §7Case §f#4"
                + "\n§7Duration: §cPermanent\n\n§7Appeal in our Discord: §9discord.gg/example"
                + "\n§8Open a ticket and pick \"Other Support\"", service.joinNote(record));
    }

    private static void assertStyle(Component component, String text, int color, boolean bold) {
        int[] colors = new int[text.length()];
        java.util.Arrays.fill(colors, color);
        assertStyle(component, text, colors, bold);
    }

    // Inspect resolved styles rather than component structure, so equivalent compositions pass.
    private static void assertStyle(Component component, String text, int[] colors, boolean bold) {
        List<Style> styles = new ArrayList<>();
        component.visit((style, value) -> {
            for (int i = 0; i < value.length(); i++) styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        int start = component.getString().indexOf(text);
        assertTrue(start >= 0, () -> "Missing text: " + text);
        for (int i = 0; i < text.length(); i++) {
            Style style = styles.get(start + i);
            assertNotNull(style.getColor());
            assertEquals(colors[i], style.getColor().getValue(), text + " color at " + i);
            assertEquals(bold, style.isBold(), text + " bold at " + i);
        }
    }
}
