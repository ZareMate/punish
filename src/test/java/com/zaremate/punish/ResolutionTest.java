package com.zaremate.punish;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.GameProfileCache;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResolutionTest extends ServerFixture {
    @Test void onlineTargetUsesCurrentProfileAndAddressInBothModes() {
        var p=player(uuid,"Alex","203.0.113.99"); when(players.getPlayerByName("Alex")).thenReturn(p);
        for(boolean ipMode:List.of(false,true)) {
            var result=service.resolve(server,"Alex",ipMode).orElseThrow();
            assertEquals("203.0.113.99",result.ip()); assertSame(p,result.player()); assertEquals(uuid,result.profile().getId());
        }
    }
    @Test void cachedTargetUsesLatestAddressAndRebuildsProfile() {
        service.db().ips().put(target.uuid(),List.of("203.0.113.1","203.0.113.2"));
        var result=service.resolve(server,"aLeX",false).orElseThrow();
        assertEquals(uuid,result.profile().getId()); assertEquals("Alex",result.name()); assertEquals("203.0.113.2",result.ip());
    }
    @Test void vanillaProfileCacheFallbackAndUnknownTarget() {
        service.db().names().clear(); var cache=mock(GameProfileCache.class); when(server.getProfileCache()).thenReturn(cache);
        when(cache.get("Alex")).thenReturn(Optional.of(new GameProfile(uuid,"Alex")));
        when(cache.get("Missing")).thenReturn(Optional.empty());
        assertEquals(uuid,service.resolve(server,"Alex",false).orElseThrow().profile().getId());
        assertTrue(service.resolve(server,"Missing",false).isEmpty());
    }
    @Test void rawAddressesRequireIpMode() {
        assertTrue(service.resolve(server,"203.0.113.10",false).isEmpty());
        assertTrue(service.resolve(server,"2001:db8::1",false).isEmpty());
        assertEquals("203.0.113.10",service.resolve(server,"203.0.113.10",true).orElseThrow().ip());
        assertEquals("2001:db8::1",service.resolve(server,"2001:db8::1",true).orElseThrow().ip());
    }
}
