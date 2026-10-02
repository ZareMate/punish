package com.zaremate.punish.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class PunishmentRecord {
    public long id;
    public String type = "";
    public boolean ipBased;
    public String uuid;
    public String name;
    public String ip;
    public String reason = "No reason given";
    public String by = "Unknown";
    public long at;
    public long until;
    public boolean active = true;
    public boolean silent;
    public boolean replaced;
    public long replacedAt;
    public String removedBy;
    public long removedAt;
    public String offense;
    public int offenseNumber;
    public long caseId;
    public boolean primary;
    public boolean delivered;
    public final List<String> seenAccounts = new ArrayList<>();

    public boolean live(long now) {
        return active && (until == 0 || until > now);
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("type", type);
        j.addProperty("ipBased", ipBased);
        if (uuid != null) j.addProperty("uuid", uuid);
        if (name != null) j.addProperty("name", name);
        if (ip != null) j.addProperty("ip", ip);
        j.addProperty("reason", reason);
        j.addProperty("by", by);
        j.addProperty("at", at);
        j.addProperty("until", until);
        j.addProperty("active", active);
        j.addProperty("silent", silent);
        j.addProperty("replaced", replaced);
        j.addProperty("replacedAt", replacedAt);
        if (removedBy != null) j.addProperty("removedBy", removedBy);
        j.addProperty("removedAt", removedAt);
        if (offense != null) {
            j.addProperty("offense", offense);
            j.addProperty("offenseNumber", offenseNumber);
            j.addProperty("caseId", caseId);
            j.addProperty("primary", primary);
        }
        j.addProperty("delivered", delivered);
        JsonArray seen = new JsonArray();
        seenAccounts.forEach(seen::add);
        j.add("seenAccounts", seen);
        return j;
    }

    public static PunishmentRecord fromJson(JsonObject j) {
        PunishmentRecord r = new PunishmentRecord();
        r.id = longValue(j, "id", 0);
        r.type = stringValue(j, "type", "");
        r.ipBased = boolValue(j, "ipBased", false);
        r.uuid = stringValue(j, "uuid", null);
        r.name = stringValue(j, "name", null);
        r.ip = stringValue(j, "ip", null);
        r.reason = stringValue(j, "reason", "No reason given");
        r.by = stringValue(j, "by", "Unknown");
        r.at = longValue(j, "at", 0);
        r.until = longValue(j, "until", 0);
        r.active = boolValue(j, "active", true);
        r.silent = boolValue(j, "silent", false);
        r.replaced = boolValue(j, "replaced", false);
        r.replacedAt = longValue(j, "replacedAt", 0);
        r.removedBy = stringValue(j, "removedBy", null);
        r.removedAt = longValue(j, "removedAt", 0);
        r.offense = stringValue(j, "offense", null);
        r.offenseNumber = (int) longValue(j, "offenseNumber", 0);
        r.caseId = longValue(j, "caseId", 0);
        r.primary = boolValue(j, "primary", false);
        r.delivered = boolValue(j, "delivered", false);
        if (j.has("seenAccounts") && j.get("seenAccounts").isJsonArray()) {
            j.getAsJsonArray("seenAccounts").forEach(x -> r.seenAccounts.add(x.getAsString()));
        }
        return r;
    }

    private static String stringValue(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    private static long longValue(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static boolean boolValue(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }
}