package com.zaremate.punish;

import com.mojang.logging.LogUtils;
import com.zaremate.punish.command.PunishCommands;
import com.zaremate.punish.event.PunishEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(PunishMod.MOD_ID)
public final class PunishMod {
    public static final String MOD_ID = "punish";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static PunishService service;

    public PunishMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(PunishCommands::register);
        PunishEvents.register();
    }

    public static synchronized PunishService service(Path serverRoot) {
        Path root = serverRoot.toAbsolutePath().normalize();
        if (service == null || !service.root().equals(root)) {
            service = new PunishService(root);
            service.load();
        }
        return service;
    }

    public static synchronized void reset() {
        service = null;
    }
}