package com.zaremate.punish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class EscalationTest extends ServerFixture {
    @ParameterizedTest @CsvSource({"0,0","1,0","2,0","3,86400000","4,172800000","5,345600000","10,345600000"})
    void warningBonuses(int count,long expected) { assertEquals(expected,service.warningBonus(count)); }
    @Test void everyConfiguredOffenseCanBeAppliedThroughEveryStep() {
        for(var offense:service.offenses()) {
            service.db().records().clear();
            for(int i=0;i<offense.steps().size()+1;i++) {
                var made=service.applyOffense(server,target,offense.id(),"test note","Staff",false);
                assertFalse(made.isEmpty(),offense.id());
                assertEquals(1,made.stream().filter(r->r.primary).count(),offense.id());
                long caseId=made.getFirst().caseId;
                for(var r:made) { assertEquals(i+1,r.offenseNumber); assertEquals(offense.id(),r.offense); assertEquals(caseId,r.caseId); assertTrue(r.reason.endsWith("test note")); }
                assertEquals(i+1,service.offenseCount(target.uuid(),offense.id()));
            }
        }
    }
    @Test void customGriefingDurationIsCappedAndFixedStepsRejectOverride() {
        var grief=service.findOffense("griefing");
        var warning=service.plan(target,grief,""); assertTrue(warning.warn());
        var capped=service.plan(target,grief,"10d note"); assertFalse(capped.warn()); assertEquals(432000000L,capped.ban()); assertEquals("note",capped.note());
        assertNotNull(service.plan(target,service.findOffense("xray"),"1d").error());
        assertThrows(IllegalArgumentException.class,()->service.applyOffense(server,target,"xray","1d","Staff",false));
        assertThrows(IllegalArgumentException.class,()->service.applyOffense(server,target,"does-not-exist","","Staff",false));
        assertTrue(service.db().records().isEmpty());
    }
    @Test void combinedPunishmentUsesWarningBonusAndOneCase() {
        for(int i=0;i<3;i++) service.create("warn",false,target,"warn","Staff",0);
        var plan=service.plan(target,service.findOffense("racism"),"");
        assertEquals(86400000L,plan.bonus()); assertEquals(345600000L,service.muteLength(plan));
        var made=service.applyOffense(server,target,"toxicity","","Staff",false);
        assertEquals(java.util.Set.of("mute","warn"),made.stream().map(r->r.type).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,service.removeOffense(server,target,made.getFirst().caseId,source));
        assertTrue(made.stream().noneMatch(r->r.active)); assertEquals(3,service.warningCount(target.uuid()));
    }
    @Test void exactOffenseIdWinsOverEarlierFuzzyMatch() {
        assertEquals("airship-grief",service.findOffense("airship-grief").id());
        assertEquals("vanilla-dupe",service.findOffense("vanilla-dupe").id());
        assertEquals("xray",service.findOffense("X-RAY").id());
        assertNull(service.findOffense("missing-offense"));
    }
}
