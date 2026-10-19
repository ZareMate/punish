package com.zaremate.punish;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import com.zaremate.punish.data.PunishmentRecord;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DiscordLoggerTest {
    @TempDir Path root;
    HttpServer http;
    BlockingQueue<String> requests=new LinkedBlockingQueue<>();
    PunishConfig config;
    @BeforeEach void setup() throws Exception {
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/",exchange->{
            requests.add(exchange.getRequestURI().getPath()+"\n"+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204,-1); exchange.close();
        }); http.start();
        config=PunishConfig.load(root); config.discordEnabled=true;
        String base="http://127.0.0.1:"+http.getAddress().getPort();
        config.warnWebhook=base+"/warn"; config.muteWebhook=base+"/mute"; config.banWebhook=base+"/ban"; config.ipWebhook=base+"/ip";
    }
    @AfterEach void stop() { if(http!=null) http.stop(0); }
    @ParameterizedTest @CsvSource({"warn,false,/warn", "mute,false,/mute", "ban,false,/ban", "kick,false,/ban", "ban,true,/ip"})
    void routesPayloadAndMasksIp(String type,boolean ip,String route) throws Exception {
        PunishmentRecord r=new PunishmentRecord(); r.id=7; r.type=type; r.name="Alex"; r.by="Staff"; r.reason="Reason"; r.ipBased=ip; r.ip="203.0.113.10"; r.at=1000; r.until=2000;
        DiscordLogger.log(config,r,false);
        String request=requests.poll(5,TimeUnit.SECONDS); assertNotNull(request); assertTrue(request.startsWith(route+"\n"));
        var body=JsonParser.parseString(request.substring(request.indexOf('\n')+1)).getAsJsonObject();
        assertEquals(config.serverName,body.get("username").getAsString());
        var embed=body.getAsJsonArray("embeds").get(0).getAsJsonObject(); assertTrue(embed.has("color"));
        String fields=embed.get("fields").toString(); assertTrue(fields.contains("Reason")); assertTrue(fields.contains("#7"));
        if(ip) { assertTrue(fields.contains("203.0.113.x")); assertFalse(fields.contains("203.0.113.10")); }
    }
    @Test void liftedPayloadIncludesRemoverAndIpv6IsMasked() throws Exception {
        var r=new PunishmentRecord(); r.type="ban"; r.ipBased=true; r.ip="2001:db8::1234"; r.removedBy="Admin";
        DiscordLogger.log(config,r,true); String request=requests.poll(5,TimeUnit.SECONDS); assertNotNull(request);
        assertTrue(request.contains("UnBan")); assertTrue(request.contains("Admin")); assertFalse(request.contains("1234"));
    }
    @Test void disabledMissingAndInvalidWebhooksDoNotSend() throws Exception {
        var r=new PunishmentRecord(); r.type="warn"; config.discordEnabled=false; DiscordLogger.log(config,r,false);
        config.discordEnabled=true; config.warnWebhook=""; DiscordLogger.log(config,r,false);
        config.warnWebhook="not a URI"; assertDoesNotThrow(()->DiscordLogger.log(config,r,false));
        assertNull(requests.poll(100,TimeUnit.MILLISECONDS));
    }
}
