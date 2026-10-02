package com.zaremate.punish.util;

public final class CommandUtil {
    private CommandUtil() {}
    public static boolean silent(String value) {
        return value != null && java.util.Arrays.asList(value.trim().split("\\s+")).contains("-s");
    }
    public static String cleanSilent(String value) {
        return value == null ? "" : value.replaceAll("(^|\\s)-s(?=\\s|$)", " ").trim();
    }
}