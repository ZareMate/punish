package com.zaremate.punish;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zaremate.punish.data.PunishmentRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

public final class DiscordLogger {
    private static final HttpClient HTTP = HttpClient.newBuilder().build();

    private DiscordLogger() {}

    public static void log(PunishConfig config, PunishmentRecord record, boolean lifted) {
        if (!config.discordEnabled) return;

        String webhook = switch (record.type) {
            case "warn" -> config.warnWebhook;
            case "mute" -> config.muteWebhook;
            case "ban" -> record.ipBased ? config.ipWebhook : config.banWebhook;
            case "kick" -> config.banWebhook;
            default -> "";
        };
        if (webhook == null || webhook.isBlank()) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", (lifted ? "Un" : "") + displayType(record.type));
        embed.addProperty("color", lifted ? 0x46A758 : color(record.type));
        embed.addProperty("timestamp", Instant.ofEpochMilli(record.at).toString());

        JsonArray fields = new JsonArray();
        field(fields, "Player", record.name);
        field(fields, "Staff", record.by);
        field(fields, "Reason", record.reason);
        field(fields, "Case", "#" + record.id);
        if (record.until > 0) field(fields, "Expires", Instant.ofEpochMilli(record.until).toString());
        if (record.ipBased && record.ip != null) field(fields, "IP", maskIp(record.ip));
        if (lifted && record.removedBy != null) field(fields, "Removed by", record.removedBy);
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.addProperty("username", config.serverName);
        body.add("embeds", embeds);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            PunishMod.LOGGER.warn("Invalid Discord webhook configured for punishment #{}", record.id);
            return;
        }

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    PunishMod.LOGGER.warn("Discord log failed for punishment #{}", record.id, ex);
                    return null;
                });
    }

    private static void field(JsonArray fields, String name, String value) {
        if (value == null || value.isBlank()) return;
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", true);
        fields.add(field);
    }

    private static int color(String type) {
        return switch (type) {
            case "ban" -> 0xE5484D;
            case "mute" -> 0xF5A524;
            case "warn" -> 0xF2C94C;
            case "kick" -> 0x8E9AAF;
            default -> 0x8E9AAF;
        };
    }

    private static String displayType(String type) {
        return switch (type) {
            case "ban" -> "Ban";
            case "mute" -> "Mute";
            case "warn" -> "Warning";
            case "kick" -> "Kick";
            default -> type;
        };
    }

    private static String maskIp(String ip) {
        if (ip.contains(":")) {
            int last = ip.lastIndexOf(':');
            return last > 0 ? ip.substring(0, last) + ":*" : ip;
        }
        int last = ip.lastIndexOf('.');
        return last > 0 ? ip.substring(0, last) + ".x" : ip;
    }
}