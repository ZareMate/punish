package com.zaremate.punish;

import com.google.gson.JsonObject;
import com.zaremate.punish.data.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StorageTest {
    @TempDir Path root;
    @Test void recordRoundTripAndExpiry() {
        PunishmentRecord r = new PunishmentRecord();
        r.id=7; r.type="ban"; r.uuid="uuid"; r.name="Alex"; r.ip="203.0.113.1";
        r.ipBased=true; r.reason="reason ☃"; r.by="Staff"; r.at=100; r.until=200;
        r.silent=true; r.replaced=true; r.replacedAt=150; r.removedBy="Admin"; r.removedAt=160;
        r.offense="xray"; r.offenseNumber=2; r.caseId=7; r.primary=true; r.delivered=true; r.seenAccounts.add("other");
        assertEquals(r.toJson(), PunishmentRecord.fromJson(r.toJson()).toJson());
        assertTrue(r.live(199)); assertFalse(r.live(200));
        r.until=0; assertTrue(r.live(Long.MAX_VALUE)); r.active=false; assertFalse(r.live(0));
        PunishmentRecord defaults = PunishmentRecord.fromJson(new JsonObject());
        assertTrue(defaults.active); assertEquals("No reason given", defaults.reason); assertNull(defaults.uuid);
    }
    @Test void databaseRoundTripAndIds() {
        Path file=root.resolve("config/db.json");
        PunishmentDatabase db=new PunishmentDatabase(file); db.load();
        PunishmentRecord r=new PunishmentRecord(); r.id=db.nextId(); db.records().add(r);
        db.names().put("id", "Alex"); db.ips().put("id", List.of("old", "new")); db.accounts().put("new", List.of("id")); db.save();
        PunishmentDatabase copy=new PunishmentDatabase(file); copy.load();
        assertEquals(2, copy.nextId()); assertEquals(r.toJson(), copy.records().getFirst().toJson());
        assertEquals(db.ips(), copy.ips()); assertEquals(db.accounts(), copy.accounts());
        assertEquals("Alex", copy.findCached("aLeX").orElseThrow().name());
        assertEquals("new", copy.findCached("Alex").orElseThrow().ip());
        assertTrue(copy.findCached("missing").isEmpty()); assertFalse(Files.exists(root.resolve("config/db.json.tmp")));
    }
    @Test void restartUsesSavedDatabaseInsteadOfOverwritingIt() {
        PunishService first=new PunishService(root); first.load();
        first.create("mute", false, new PunishService.Target("Alex", "id", null, null, null), "test", "Staff", 0); first.save();
        PunishService second=new PunishService(root); second.load();
        assertEquals(1, second.db().records().size()); assertEquals(2, second.db().nextId());
    }
    @Test void migrationRunsOnlyWhenCurrentDatabaseIsAbsent() throws Exception {
        Path legacy=root.resolve("local/kubejs/punishments.json"); Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "{\"next\":8,\"records\":[{\"id\":7,\"type\":\"warn\"}]}");
        PunishService first=new PunishService(root); first.load(); assertEquals(7, first.db().records().getFirst().id);
        first.db().records().clear(); first.save();
        PunishService second=new PunishService(root); second.load(); assertTrue(second.db().records().isEmpty());
        assertTrue(Files.exists(legacy));
    }
    @Test void configDefaultsCustomValuesAndMalformedFallback() throws Exception {
        PunishConfig c=PunishConfig.load(root); assertFalse(c.discordEnabled); assertEquals("", c.banWebhook);
        Path file=root.resolve("config/punish-config.json"); assertTrue(Files.exists(file));
        Files.writeString(file, "{\"serverName\":\"Test\",\"discordEnabled\":true}");
        c=PunishConfig.load(root); assertEquals("Test", c.serverName); assertTrue(c.discordEnabled); assertEquals("", c.warnWebhook);
        Files.writeString(file, "broken"); assertFalse(PunishConfig.load(root).discordEnabled);
        assertEquals("broken", Files.readString(file));
    }
}
