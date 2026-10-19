package com.zaremate.punish;

import com.zaremate.punish.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiTest extends ServerFixture {
    @ParameterizedTest @EnumSource(PunishmentType.class)
    void apiCreatesAllTypesAndReturnsImmutableSnapshot(PunishmentType type) {
        var p=player(uuid,"Alex",target.ip()); when(players.getPlayer(uuid)).thenReturn(p);
        var result=PunishApi.addPunishment(server,uuid,"Alex",type,0,"API reason","Integration");
        assertEquals(type,result.type()); assertEquals(uuid,result.uuid()); assertEquals("Integration",result.by());
        assertEquals(type!=PunishmentType.KICK,result.isCurrent()); assertTrue(result.isPermanent());
        service.db().records().getFirst().reason="changed"; assertEquals("API reason",result.reason());
        assertEquals(type==PunishmentType.KICK?0:1,PunishApi.getCurrentPunishments(p).size());
        assertEquals(type!=PunishmentType.KICK,PunishApi.getCurrentPunishment(server,uuid,type).isPresent());
    }
    @Test void apiRejectsInvalidArgumentsWithoutCreatingRecords() {
        assertThrows(IllegalArgumentException.class,()->PunishApi.addPunishment(server,uuid,"Alex",PunishmentType.BAN,-1,"reason","Staff"));
        assertThrows(IllegalArgumentException.class,()->PunishApi.addPunishment(server,uuid,"Alex",PunishmentType.BAN,true,"",0,"reason","Staff",false));
        assertThrows(IllegalArgumentException.class,()->PunishApi.addPunishment(server,uuid,"Alex",PunishmentType.KICK,true,target.ip(),0,"reason","Staff",false));
        assertThrows(IllegalArgumentException.class,()->PunishApi.addPunishment(server,uuid,"Alex",PunishmentType.KICK,0,"reason","Staff"));
        assertThrows(NullPointerException.class,()->PunishApi.getCurrentPunishments(server,null));
        assertThrows(NullPointerException.class,()->PunishApi.getCurrentPunishments((net.minecraft.server.level.ServerPlayer)null));
        assertTrue(service.db().records().isEmpty());
    }
    @Test void offenseApiEscalatesAndHistoryContainsOnlyPrimaryRecords() {
        var first=PunishApi.addPunishment(server,uuid,"Alex","toxicity","note -s","Integration",false);
        assertEquals(2,first.size()); assertTrue(first.stream().allMatch(Punishment::silent));
        assertEquals(1,PunishApi.getRecordedOffenses(server,uuid).size());
        var second=PunishApi.addPunishment(server,uuid,"Alex","toxicity","Integration");
        assertEquals(PunishmentType.BAN,second.getFirst().type()); assertEquals(2,second.getFirst().offenseNumber());
        var history=PunishApi.getRecordedOffenses(server,uuid); assertEquals(2,history.size());
        assertThrows(UnsupportedOperationException.class,()->history.clear());
        assertEquals(service.offenses(),PunishApi.getOffenses(server));
        assertThrows(UnsupportedOperationException.class,()->PunishApi.getOffenses(server).clear());
    }
    @Test void expiredAndRemovedApiRecordsAreNotCurrent() {
        var r=PunishApi.addPunishment(server,uuid,"Alex",PunishmentType.MUTE,100000,"reason","Staff");
        assertFalse(r.isExpired()); assertFalse(r.isPermanent());
        var expired=new Punishment(r.id(),r.type(),false,uuid,"Alex",null,"reason","Staff",0,1,true,false,null,0);
        assertTrue(expired.isExpired()); assertFalse(expired.isCurrent());
        service.db().records().getFirst().active=false; assertTrue(PunishApi.getCurrentPunishments(server,uuid).isEmpty());
    }
}
