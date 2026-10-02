package com.zaremate.punish.event;

import com.zaremate.punish.PunishMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class PunishEvents {
    private static final PunishEvents INSTANCE = new PunishEvents();

    private PunishEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        var service = PunishMod.service(player.getServer().getServerDirectory().toAbsolutePath());
        service.recordJoin(player);
        service.alertSharedIp(player);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player.getServer() == null) return;
        var service = PunishMod.service(player.getServer().getServerDirectory().toAbsolutePath());
        service.activeMute(player.getUUID().toString(), service.ip(player)).ifPresent(mute -> {
            player.sendSystemMessage(service.muteComponent(mute));
            event.setCanceled(true);
        });
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;

        String raw = event.getParseResults().getReader().getString().replaceFirst("^/", "");
        String command = raw.split("\\s+", 2)[0].toLowerCase();
        var service = PunishMod.service(player.getServer().getServerDirectory().toAbsolutePath());
        if (!service.mutedCommands().contains(command)) return;

        service.activeMute(player.getUUID().toString(), service.ip(player)).ifPresent(mute -> {
            player.sendSystemMessage(service.muteComponent(mute));
            event.setCanceled(true);
        });
    }
}