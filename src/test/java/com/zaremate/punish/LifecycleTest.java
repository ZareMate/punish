package com.zaremate.punish;

import com.zaremate.punish.data.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleTest extends ServerFixture {
    @Test void replacementMatchesUuidRegardlessOfIpAndIpRegardlessOfUuid() {
        var old=service.create("mute", false, target, "old", "Staff", 0);
        var changed=new PunishService.Target("Alex", target.uuid(), "203.0.113.20", target.profile(), null);
        service.replaceActiveEquivalent("mute", false, changed);
        assertFalse(old.active); assertTrue(old.replaced); assertTrue(old.replacedAt>0);
        var ip=service.create("ban", true, target, "old", "Staff", 0);
        service.replaceActiveEquivalent("ban", true, new PunishService.Target("Other", "other", target.ip(), null, null));
        assertFalse(ip.active);
        var unrelated=service.create("warn", false, target, "keep", "Staff", 0);
        service.replaceActiveEquivalent("mute", false, target); assertTrue(unrelated.active);
    }
    @Test void queriesRespectIdentityTypeExpiryAndHistoricalIps() {
        var own=service.create("warn", false, target, "own", "Staff", 0);
        var ip=service.create("mute", true, new PunishService.Target("Other", "other", target.ip(), null, null), "ip", "Staff", 0);
        var expired=service.create("ban", false, target, "expired", "Staff", 1); expired.until=1;
        var inactive=service.create("mute", false, target, "inactive", "Staff", 0); inactive.active=false;
        service.create("warn", false, new PunishService.Target("Other", "other", "other", null, null), "other", "Staff", 0);
        assertEquals(List.of(own,ip), service.currentPunishments(target.uuid(), "new"));
        assertEquals(ip, service.activeMute(target.uuid(), target.ip()).orElseThrow());
        assertTrue(service.activeMute(target.uuid(), "new").isEmpty());
        assertEquals(ip, service.activePunishment(target.uuid(), "new", "mute").orElseThrow());
        assertTrue(service.activePunishment(target.uuid(), null, "ban").isEmpty());
        assertEquals(1, service.warningCount(target.uuid()));
    }
    @Test void banPersistsVanillaEntryAndDisconnectsOnlyTarget() {
        var p=player(uuid,"Alex",target.ip());
        var online=new PunishService.Target("Alex",target.uuid(),target.ip(),target.profile(),p);
        var r=service.addApiBan(server,online,false,60000,"Reason","Staff",false);
        assertTrue(players.getBans().isBanned(target.profile()));
        assertEquals(60000,r.until-r.at); verify(p.connection).disconnect(any(Component.class));
        verify(players).broadcastSystemMessage(any(Component.class),eq(false));
        var reload=new PunishService(root); reload.load(); assertEquals(r.id,reload.db().records().getFirst().id);
    }
    @Test void ipBanDisconnectsMatchingAddressesOnly() {
        var a=player(uuid,"Alex",target.ip()); var b=player(UUID.randomUUID(),"Other","203.0.113.20");
        when(players.getPlayers()).thenReturn(List.of(a,b));
        service.addApiBan(server,target,true,0,"Reason","Staff",false);
        assertTrue(players.getIpBans().isBanned(target.ip()));
        verify(a.connection).disconnect(any(Component.class)); verify(b.connection,never()).disconnect(any(Component.class));
    }
    @Test void warningsAreDeliveredOnceOnJoinAndIpWarningsOncePerAccount() {
        var warning=service.addApiWarn(server,target,false,"offline","Staff",false);
        assertFalse(warning.delivered);
        var p=player(uuid,"Alex",target.ip()); service.recordJoin(p); service.recordJoin(p);
        verify(p,times(1)).sendSystemMessage(any(Component.class)); assertTrue(warning.delivered);
        var ip=service.create("warn",true,target,"ip","Staff",0);
        service.recordJoin(p); service.recordJoin(p); assertEquals(List.of(target.uuid()),ip.seenAccounts);
        verify(p,times(2)).sendSystemMessage(any(Component.class));
        assertEquals(List.of(target.ip()),service.db().ips().get(target.uuid()));
        assertEquals(List.of(target.uuid()),service.db().accounts().get(target.ip()));
    }
    @Test void onlineWarningIsNotRepeatedAndKickIsHistoryOnly() {
        var p=player(uuid,"Alex",target.ip()); var online=new PunishService.Target("Alex",target.uuid(),target.ip(),target.profile(),p);
        var warning=service.addApiWarn(server,online,false,"warn","Staff",false);
        assertTrue(warning.delivered); service.recordJoin(p); verify(p,times(1)).sendSystemMessage(any(Component.class));
        var kick=service.addApiKick(online,"kick","Staff",false); assertFalse(kick.active);
        verify(p.connection).disconnect(any(Component.class));
        assertThrows(IllegalArgumentException.class,()->service.addApiKick(target,"kick","Staff",false));
    }
    @Test void removeOffenseKeepsOtherCasesAndTheirVanillaBan() {
        var first=service.applyOffense(server,target,"xray","","Staff",false).getFirst();
        var second=service.applyOffense(server,target,"xray","","Staff",false).getFirst();
        assertTrue(second.until==0); assertEquals(2,service.offenseCount(target.uuid(),"xray"));
        assertEquals(1,service.removeOffense(server,target,first.caseId,source));
        assertTrue(second.active); assertTrue(players.getBans().isBanned(target.profile()));
        assertEquals(1,service.offenseCount(target.uuid(),"xray"));
        assertEquals(1,service.removeOffense(server,target,second.caseId,source));
        assertFalse(players.getBans().isBanned(target.profile())); assertEquals(0,service.offenseCount(target.uuid(),"xray"));
        assertEquals(0,service.removeOffense(server,target,9999,source));
    }
    @Test void removingOneIpOffensePreservesOtherLiveIpBan() {
        var first=service.addApiBan(server,target,true,0,"first","Staff",false);
        first.offense="xray"; first.primary=true; first.caseId=first.id;
        var other=new PunishService.Target("Other","other",target.ip(),null,null);
        var second=service.create("ban",true,other,"second","Staff",0);
        assertEquals(1,service.removeOffense(server,target,first.id,source));
        assertTrue(second.active); assertTrue(players.getIpBans().isBanned(target.ip()));
        second.offense="xray"; second.caseId=second.id; second.primary=true;
        assertEquals(1,service.removeOffense(server,other,second.id,source));
        assertFalse(players.getIpBans().isBanned(target.ip()));
    }
    @Test void liftingRecordsPreservesAuditInformation() {
        var record=service.create("mute",false,target,"reason","Staff",0);
        service.lift(record,source);
        assertFalse(record.active); assertEquals("Moderator",record.removedBy); assertTrue(record.removedAt>0);
        assertEquals("reason",record.reason); assertEquals("Staff",record.by);
    }
    @ParameterizedTest @ValueSource(strings={"127.0.0.1","10.1.2.3","172.16.0.1","172.31.255.1","192.168.1.1","169.254.0.1","::1","fd00::1","fe80::1"})
    void localAddressesAreProtected(String ip) { assertTrue(service.isLocalOrProxyIp(ip)); }
    @ParameterizedTest @ValueSource(strings={"203.0.113.1","172.15.0.1","172.32.0.1","2001:db8::1"})
    void publicAddressesAreAllowed(String ip) { assertFalse(service.isLocalOrProxyIp(ip)); }
}
