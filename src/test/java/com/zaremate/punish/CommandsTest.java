package com.zaremate.punish;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zaremate.punish.command.PunishCommands;
import net.minecraft.commands.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandsTest extends ServerFixture {
    CommandDispatcher<CommandSourceStack> dispatcher;
    @BeforeEach void commands() {
        dispatcher=new CommandDispatcher<>();
        PunishCommands.register(new RegisterCommandsEvent(dispatcher,Commands.CommandSelection.DEDICATED,null));
        // Render lazy feedback too, so tests catch errors in message suppliers.
        doAnswer(a->{ ((java.util.function.Supplier<?>)a.getArgument(0)).get(); return null; }).when(source).sendSuccess(any(),anyBoolean());
    }
    @ParameterizedTest @CsvSource({
        "ban Alex 1h reason,ban,false,3600000", "tempban Alex 1h reason,ban,false,3600000",
        "ipban Alex 1h reason,ban,true,3600000", "tempipban Alex 1h reason,ban,true,3600000",
        "mute Alex 1h reason,mute,false,3600000", "tempmute Alex 1h reason,mute,false,3600000",
        "ipmute Alex 1h reason,mute,true,3600000", "warn Alex reason,warn,false,0", "ipwarn Alex reason,warn,true,0",
        "ban-ip Alex,ban,true,0"})
    void createsExpectedPunishment(String input,String type,boolean ip,long duration) throws Exception {
        assertEquals(1,dispatcher.execute(input,source)); var r=service.db().records().getLast();
        assertEquals(type,r.type); assertEquals(ip,r.ipBased); assertEquals(duration,r.until==0?0:r.until-r.at);
        assertEquals("Moderator",r.by);
    }
    @ParameterizedTest @ValueSource(strings={"tempban Alex", "tempmute Alex", "tempipban Alex", "tempban Alex nonsense", "ipban 192.168.1.1", "ban Missing", "kick Alex", "punish Alex nonexistent note"})
    void invalidCommandsDoNotMutate(String command) throws Exception {
        assertEquals(0,dispatcher.execute(command,source)); assertTrue(service.db().records().isEmpty());
    }
    @ParameterizedTest @CsvSource({"ban,unban", "ban,pardon", "ipban,unbanip", "ipban,pardon-ip", "mute,unmute", "warn,unwarn", "warn,warn_remove"})
    void removalCommandsLiftAndPersist(String add,String remove) throws Exception {
        dispatcher.execute(add+" Alex",source); assertEquals(1,dispatcher.execute(remove+" Alex",source));
        var r=service.db().records().getFirst(); assertFalse(r.active); assertEquals("Moderator",r.removedBy);
        var reloaded=new PunishService(root); reloaded.load(); assertFalse(reloaded.db().records().getFirst().active);
        if(add.equals("ban")) assertFalse(players.getBans().isBanned(target.profile()));
        if(add.equals("ipban")) assertFalse(players.getIpBans().isBanned(target.ip()));
    }
    @Test void reportingCommandsAndOffenseRemoval() throws Exception {
        dispatcher.execute("warn Alex reason",source);
        assertEquals(1,dispatcher.execute("warnings Alex",source));
        assertEquals(1,dispatcher.execute("history Alex",source));
        assertEquals(1,dispatcher.execute("staffhistory moderator",source));
        assertEquals(1,dispatcher.execute("checkban Alex",source));
        assertEquals(1,dispatcher.execute("checkmute Alex",source));
        assertEquals(0,dispatcher.execute("dupeip Alex",source));
        service.db().accounts().put(target.ip(),java.util.List.of(target.uuid(),"other"));
        service.db().names().put("other","Other"); assertEquals(1,dispatcher.execute("dupeip Alex",source));
        assertEquals(1,dispatcher.execute("punish Alex",source));
        assertEquals(1,dispatcher.execute("punish Alex xray",source)); // preview
        assertEquals(1,service.db().records().size());
        assertEquals(1,dispatcher.execute("punish Alex xray evidence",source));
        var offense=service.db().records().getLast(); assertEquals("xray",offense.offense);
        assertEquals(1,dispatcher.execute("unoffense Alex "+offense.caseId,source)); assertFalse(offense.active);
    }
    @Test void silentFlagIsRemovedFromReasonAndKickDisconnects() throws Exception {
        dispatcher.execute("mute Alex reason -s",source); var mute=service.db().records().getFirst();
        assertTrue(mute.silent); assertEquals("reason",mute.reason);
        verify(players,never()).broadcastSystemMessage(any(),anyBoolean());
        var p=player(uuid,"Alex",target.ip()); when(players.getPlayerByName("Alex")).thenReturn(p);
        dispatcher.execute("kick Alex reason",source); assertEquals("kick",service.db().records().getLast().type);
        verify(p.connection).disconnect(any(net.minecraft.network.chat.Component.class));
    }
    @Test void consolePermissionAndSyntaxFailures() {
        when(source.hasPermission(4)).thenReturn(false);
        for(var command:dispatcher.getRoot().getChildren()) assertFalse(command.canUse(source),command.getName());
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute("ban Alex",source));
        when(source.hasPermission(4)).thenReturn(true);
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute("unoffense Alex 0",source));
        assertTrue(service.db().records().isEmpty());
    }
    @Test void suggestionsIncludePlayersOffensesAndCaseIds() throws Exception {
        var p=player(uuid,"Alex",target.ip()); when(players.getPlayers()).thenReturn(java.util.List.of(p));
        assertTrue(suggestions("punish Al").contains("Alex"));
        assertTrue(suggestions("punish Alex xr").contains("xray"));
        dispatcher.execute("punish Alex xray evidence",source);
        assertTrue(suggestions("unoffense Alex ").contains(Long.toString(service.db().records().getFirst().caseId)));
    }
    @ParameterizedTest
    @ValueSource(strings={"ban", "tempban", "ipban", "tempipban", "unban", "unbanip",
            "mute", "tempmute", "ipmute", "unmute", "warn", "ipwarn", "unwarn", "warn_remove",
            "warnings", "kick", "history", "checkban", "checkmute", "dupeip", "staffhistory",
            "punish", "unoffense", "pardon", "ban-ip", "pardon-ip"})
    void everyPlayerArgumentSuggestsOnlineAndPersistedOfflineNames(String command) throws Exception {
        var online=player(java.util.UUID.randomUUID(), "Alfred", target.ip());
        when(players.getPlayers()).thenReturn(java.util.List.of(online));
        service.db().names().put("duplicate", "Alex");
        service.save();
        PunishMod.reset(); // Offline names must survive reloading the database.
        assertEquals(java.util.List.of("Alex", "Alfred"), suggestions(command+" aL"));
        assertTrue(suggestions(command+" nobody").isEmpty());
    }

    @Test void warningRemovalSelectsOnlyOwnedActivePlayerWarnings() throws Exception {
        dispatcher.execute("warn Alex first", source);
        var first=service.db().records().getLast();
        dispatcher.execute("warn Alex second", source);
        var second=service.db().records().getLast();
        var mute=service.create("mute", false, target, "mute", "Staff", 0);
        var ipwarn=service.create("warn", true, target, "ip", "Staff", 0);
        var other=service.create("warn", false, target, "other", "Staff", 0);
        other.uuid=java.util.UUID.randomUUID().toString();
        for (long id : new long[]{mute.id, ipwarn.id, other.id, 9999}) {
            assertEquals(0,dispatcher.execute("warn_remove Alex "+id,source));
        }
        assertEquals(1,dispatcher.execute("warn_remove Alex "+first.id,source));
        assertFalse(first.active); assertTrue(second.active);
        assertEquals("Moderator",first.removedBy); assertTrue(first.removedAt>0);
        assertEquals(0,dispatcher.execute("warn_remove Alex "+first.id,source));
        assertEquals(1,dispatcher.execute("warn_remove Alex",source));
        assertFalse(second.active);
        assertEquals(0,dispatcher.execute("warn_remove Alex",source));
        assertTrue(mute.active); assertTrue(ipwarn.active); assertTrue(other.active);
        var reloaded=new PunishService(root); reloaded.load();
        assertFalse(reloaded.db().records().getFirst().active);
        assertThrows(CommandSyntaxException.class,()->dispatcher.execute("warn_remove Alex 0",source));
    }

    java.util.List<String> suggestions(String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input,source)).get().getList().stream().map(s->s.getText()).toList();
    }
}
