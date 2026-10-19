package com.zaremate.punish.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Script-style templates; placeholder values remain literal text. */
public final class ChatText {
    public static final String BAD = "<#8B1E1E>[<#FF4343>&l!<#8B1E1E>] ";
    public static final String GOOD = "<#479F2A>[<#72FF43>&l!<#479F2A>] ";
    public static final String NOTE = "<#8B6A1E>[<#FFC043>&l?<#8B6A1E>] ";
    public static final String SILENT = "<#555555>[<#AAAAAA>&lS<#555555>] ";
    public static final String TITLE = "<#4498DB>&lC<#53A0DE>&lR<#63A9E1>&lE<#72B1E3>&lA<#82BAE6>&lT<#91C2E9>&lE"
            + "<#82BAE6>&lC<#72B1E3>&lR<#63A9E1>&lA<#53A0DE>&lF<#4498DB>&lT";
    private static final Pattern TOKEN = Pattern.compile("<#([0-9a-fA-F]{6})>|&([0-9a-fA-FlLrR])|\\{(\\d+)\\}");

    private ChatText() {}

    public static MutableComponent format(String template, Object... values) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY.withColor(0xFFFFFF).withBold(false);
        Matcher matcher = TOKEN.matcher(template);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last)
                result.append(Component.literal(template.substring(last, matcher.start())).setStyle(style));
            if (matcher.group(1) != null) {
                style = style.withColor(Integer.parseInt(matcher.group(1), 16)).withBold(false);
            } else if (matcher.group(2) != null) {
                char code = Character.toLowerCase(matcher.group(2).charAt(0));
                if (code == 'l') style = style.withBold(true);
                else if (code == 'r') style = Style.EMPTY.withColor(0xFFFFFF).withBold(false);
                else style = style.withColor(ChatFormatting.getByCode(code)).withBold(false);
            } else {
                result.append(Component.literal(String.valueOf(values[Integer.parseInt(matcher.group(3))])).setStyle(style));
            }
            last = matcher.end();
        }
        if (last < template.length()) result.append(Component.literal(template.substring(last)).setStyle(style));
        return result;
    }
}
