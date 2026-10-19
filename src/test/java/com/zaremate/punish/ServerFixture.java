package com.zaremate.punish;

import com.mojang.authlib.GameProfile;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.*;

abstract class ServerFixture {
    static {
        SharedConstants.tryDetectVersion();
        // Unit tests have no mod discovery phase; only vanilla flags are needed.
        try (var flags = mockStatic(net.neoforged.neoforge.common.util.flag.FeatureFlagLoader.class)) {
            Bootstrap.bootStrap();
        }
    }
    @TempDir Path root;
    MinecraftServer server;
    PlayerList players;
    CommandSourceStack source;
    PunishService service;
    UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    PunishService.Target target;

    @BeforeEach void setupServer() {
        PunishMod.reset();
        server = mock(MinecraftServer.class);
        players = mock(PlayerList.class);
        source = mock(CommandSourceStack.class);
        when(server.getServerDirectory()).thenReturn(root);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of());
        when(players.getBans()).thenReturn(new UserBanList(root.resolve("banned-players.json").toFile()));
        when(players.getIpBans()).thenReturn(new IpBanList(root.resolve("banned-ips.json").toFile()));
        when(source.getServer()).thenReturn(server);
        when(source.getTextName()).thenReturn("Moderator");
        when(source.hasPermission(4)).thenReturn(true);
        service = PunishMod.service(root);
        target = new PunishService.Target("Alex", uuid.toString(), "203.0.113.10", new GameProfile(uuid, "Alex"), null);
        service.db().names().put(uuid.toString(), "Alex");
        service.db().ips().put(uuid.toString(), new java.util.ArrayList<>(List.of(target.ip())));
    }

    ServerPlayer player(UUID id, String name, String ip) {
        ServerPlayer p = mock(ServerPlayer.class);
        p.connection = mock(ServerGamePacketListenerImpl.class);
        when(p.getUUID()).thenReturn(id);
        when(p.getGameProfile()).thenReturn(new GameProfile(id, name));
        when(p.getServer()).thenReturn(server);
        when(p.connection.getRemoteAddress()).thenReturn(new InetSocketAddress(ip, 25565));
        return p;
    }

    @AfterEach void resetService() { PunishMod.reset(); }
}
