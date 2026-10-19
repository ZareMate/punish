package com.zaremate.punish;

import com.mojang.brigadier.CommandDispatcher;
import com.zaremate.punish.event.PunishEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventsTest extends ServerFixture {
    PunishEvents events=mock(PunishEvents.class,CALLS_REAL_METHODS);
    @Test void chatMuteCancelsAndExpiryRestoresChat() {
        var p=player(uuid,"Alex",target.ip());
        var mute=service.create("mute",false,target,"reason","Staff",0);
        var chat=new ServerChatEvent(p,"hello",Component.literal("hello")); events.onChat(chat); assertTrue(chat.isCanceled());
        verify(p).sendSystemMessage(any(Component.class));
        mute.until=1;
        var allowed=new ServerChatEvent(p,"hello",Component.literal("hello")); events.onChat(allowed); assertFalse(allowed.isCanceled());
    }
    @ParameterizedTest @ValueSource(strings={"msg", "tell", "w", "me", "say", "teammsg", "tm", "r", "reply", "minecraft:msg", "/MINECRAFT:TELL"})
    void mutedCommandsCannotBypassWithNamespace(String command) {
        var p=player(uuid,"Alex",target.ip()); when(source.getEntity()).thenReturn(p);
        service.create("mute",true,target,"reason","Staff",0);
        var event=new CommandEvent(new CommandDispatcher<CommandSourceStack>().parse(command+" Other hi",source));
        events.onCommand(event); assertTrue(event.isCanceled(),command);
    }
    @Test void unrelatedCommandsAndConsoleAreNotBlocked() {
        var p=player(uuid,"Alex",target.ip()); when(source.getEntity()).thenReturn(p);
        service.create("mute",false,target,"reason","Staff",0);
        var d=new CommandDispatcher<CommandSourceStack>(); var event=new CommandEvent(d.parse("help",source));
        events.onCommand(event); assertFalse(event.isCanceled());
        when(source.getEntity()).thenReturn(null); event=new CommandEvent(d.parse("say test",source)); events.onCommand(event); assertFalse(event.isCanceled());
    }
    @Test void loginRecordsNameAndDoesNotTrackProxyAddresses() {
        var p=player(uuid,"Renamed","192.168.1.1"); service.db().ips().clear();
        events.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        assertEquals("Renamed",service.db().names().get(target.uuid())); assertTrue(service.db().ips().isEmpty());
        assertTrue(service.db().accounts().isEmpty());
    }
}
