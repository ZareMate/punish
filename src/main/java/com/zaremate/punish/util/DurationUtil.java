package com.zaremate.punish.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)(y|mo|w|d|h|m|s)");
    private DurationUtil() {}

    public static long parse(String input) {
        if (input == null || input.isBlank()) return -1;
        String value = input.toLowerCase();
        if (!value.matches("(?:\\d+(?:y|mo|w|d|h|m|s))+")) return -1;
        Matcher matcher = TOKEN.matcher(value);
        long total = 0;
        while (matcher.find()) {
            long n = Long.parseLong(matcher.group(1));
            total += n * switch (matcher.group(2)) {
                case "y" -> 31_536_000_000L;
                case "mo" -> 2_592_000_000L;
                case "w" -> 604_800_000L;
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m" -> 60_000L;
                default -> 1_000L;
            };
        }
        return total;
    }

    public static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append('d');
        if (h > 0) { if (!out.isEmpty()) out.append(' '); out.append(h).append('h'); }
        if (m > 0 && d == 0) { if (!out.isEmpty()) out.append(' '); out.append(m).append('m'); }
        if (out.isEmpty()) out.append(s).append('s');
        return out.toString();
    }
}