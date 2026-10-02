package com.zaremate.punish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PunishConfig {
    public boolean discordEnabled = false;
    public String serverName = "CreateCraft Season 6";
    public String warnWebhook = "";
    public String muteWebhook = "";
    public String banWebhook = "";
    public String ipWebhook = "";

    private PunishConfig() {}

    public static PunishConfig load(Path root) {
        Path file = root.resolve("config").resolve("punish-config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                PunishConfig config = new PunishConfig();
                Files.writeString(file, gson.toJson(config));
                return config;
            }
            PunishConfig config = gson.fromJson(Files.readString(file), PunishConfig.class);
            return config == null ? new PunishConfig() : config;
        } catch (Exception e) {
            PunishMod.LOGGER.error("Could not load {}", file, e);
            return new PunishConfig();
        }
    }
}