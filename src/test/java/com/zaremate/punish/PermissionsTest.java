package com.zaremate.punish;

import com.mojang.brigadier.CommandDispatcher;
import com.zaremate.punish.command.PunishCommands;
import net.minecraft.commands.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionsTest extends ServerFixture {
    @Test void everyCommandChecksItsRegisteredNodeAndAliasesShareNodes() {
        var event=new PermissionGatherEvent.Nodes(); PunishPermissions.register(event);
        var dispatcher=new CommandDispatcher<CommandSourceStack>();
        PunishCommands.register(new RegisterCommandsEvent(dispatcher,Commands.CommandSelection.DEDICATED,null));
        var p=player(uuid,"Alex",target.ip()); when(source.getEntity()).thenReturn(p);
        var aliases=Map.ofEntries(
                Map.entry("tempban","ban"), Map.entry("unban","ban"), Map.entry("pardon","ban"),
                Map.entry("tempipban","ipban"), Map.entry("unbanip","ipban"),
                Map.entry("ban-ip","ipban"), Map.entry("pardon-ip","ipban"),
                Map.entry("tempmute","mute"), Map.entry("unmute","mute"),
                Map.entry("unwarn","warn"), Map.entry("warn_remove","warn"),
                Map.entry("unoffense","punish"));
        try(var api=mockStatic(PermissionAPI.class)) {
            for(var command:dispatcher.getRoot().getChildren()) {
                String expected="punish."+aliases.getOrDefault(command.getName(),command.getName());
                @SuppressWarnings("unchecked") var node=(PermissionNode<Boolean>)event.getNodes().stream().filter(n->n.getNodeName().equals(expected)).findFirst().orElseThrow();
                api.when(()->PermissionAPI.getPermission(p,node)).thenReturn(true);
                when(source.hasPermission(2)).thenReturn(false);
                assertTrue(command.canUse(source),expected+" non-op grant");
                api.when(()->PermissionAPI.getPermission(p,node)).thenReturn(false);
                when(source.hasPermission(2)).thenReturn(true);
                assertFalse(command.canUse(source),expected+" op denial");
            }
        }
    }
    @Test void defaultsRequireOpAndOfflineDefaultsDeny() {
        var event=new PermissionGatherEvent.Nodes(); PunishPermissions.register(event);
        var p=player(uuid,"Alex",target.ip());
        for(var node:event.getNodes()) {
            when(p.hasPermissions(2)).thenReturn(false); assertEquals(false,node.getDefaultResolver().resolve(p,uuid));
            when(p.hasPermissions(2)).thenReturn(true); assertEquals(true,node.getDefaultResolver().resolve(p,uuid));
            assertEquals(false,node.getDefaultResolver().resolve(null,uuid));
        }
    }
    @Test void silentAnnouncementsAndAltAlertsUseIndependentRecipientPermissions() {
        var allowed=player(uuid,"Alex",target.ip()); var denied=player(UUID.randomUUID(),"Other",target.ip());
        when(players.getPlayers()).thenReturn(List.of(allowed,denied));
        try(var api=mockStatic(PermissionAPI.class)) {
            api.when(()->PermissionAPI.getPermission(allowed,PunishPermissions.NOTIFY_SILENT)).thenReturn(true);
            api.when(()->PermissionAPI.getPermission(denied,PunishPermissions.NOTIFY_SILENT)).thenReturn(false);
            var r=service.create("warn",false,target,"test","Staff",0); r.silent=true; service.announce(server,r);
            verify(allowed).sendSystemMessage(any()); verify(denied,never()).sendSystemMessage(any());
            clearInvocations(allowed,denied);
            api.when(()->PermissionAPI.getPermission(denied,PunishPermissions.ALERTS_ALTS)).thenReturn(true);
            api.when(()->PermissionAPI.getPermission(allowed,PunishPermissions.ALERTS_ALTS)).thenReturn(false);
            service.db().accounts().put(target.ip(),List.of(target.uuid(),denied.getUUID().toString()));
            service.alertSharedIp(allowed);
            verify(denied).sendSystemMessage(any()); verify(allowed,never()).sendSystemMessage(any());
        }
    }
}
