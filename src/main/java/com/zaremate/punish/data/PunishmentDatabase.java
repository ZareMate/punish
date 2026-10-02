package com.zaremate.punish.data;

import com.google.gson.*;
import com.zaremate.punish.PunishService;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PunishmentDatabase {
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private long next = 1;
    private final List<PunishmentRecord> records = new ArrayList<>();
    private final Map<String, List<String>> ips = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, List<String>> accounts = new HashMap<>();

    public PunishmentDatabase(Path file) { this.file = file; }

    public synchronized void load() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) { save(); return; }
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            next = root.has("next") ? root.get("next").getAsLong() : 1;
            records.clear();
            if (root.has("records")) root.getAsJsonArray("records").forEach(x -> records.add(PunishmentRecord.fromJson(x.getAsJsonObject())));
            readListMap(root, "ips", ips);
            readListMap(root, "accounts", accounts);
            names.clear();
            if (root.has("names")) root.getAsJsonObject("names").entrySet()
                    .forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            com.zaremate.punish.PunishMod.LOGGER.error("Could not load punishment database {}", file, e);
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("next", next);
            JsonArray r = new JsonArray();
            records.forEach(x -> r.add(x.toJson()));
            root.add("records", r);
            root.add("ips", listMapJson(ips));
            root.add("names", stringMapJson(names));
            root.add("accounts", listMapJson(accounts));

            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(root));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            com.zaremate.punish.PunishMod.LOGGER.error("Could not save punishment database {}", file, e);
        }
    }

    private void readListMap(JsonObject root, String key, Map<String, List<String>> destination) {
        destination.clear();
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        root.getAsJsonObject(key).entrySet().forEach(e -> {
            List<String> values = new ArrayList<>();
            if (e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(x -> values.add(x.getAsString()));
            destination.put(e.getKey(), values);
        });
    }

    private JsonObject listMapJson(Map<String, List<String>> values) {
        JsonObject o = new JsonObject();
        values.forEach((k, v) -> { JsonArray a = new JsonArray(); v.forEach(a::add); o.add(k, a); });
        return o;
    }

    private JsonObject stringMapJson(Map<String, String> values) {
        JsonObject o = new JsonObject();
        values.forEach(o::addProperty);
        return o;
    }

    public long nextId() { return next++; }
    public List<PunishmentRecord> records() { return records; }
    public Map<String, List<String>> ips() { return ips; }
    public Map<String, String> names() { return names; }
    public Map<String, List<String>> accounts() { return accounts; }

    public Optional<PunishService.GameProfileHolder> findCached(String name) {
        return names.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(name))
                .findFirst()
                .map(e -> new PunishService.GameProfileHolder(e.getValue(), e.getKey(),
                        ips.getOrDefault(e.getKey(), List.of()).stream().findFirst().orElse(null)));
    }
}