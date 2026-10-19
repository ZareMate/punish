package com.zaremate.punish;

import com.zaremate.punish.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilityTest {
    @ParameterizedTest @CsvSource({"1s,1000", "30m,1800000", "1H,3600000", "1d12h,129600000", "1w,604800000", "1mo,2592000000", "1y,31536000000", "0s,0"})
    void durationUnits(String input, long expected) { assertEquals(expected, DurationUtil.parse(input)); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "-1d", "1.5h", "1h junk", "1", "perm", "1ms", " 1d", "999999999999999999999999999999d", "9223372036854775807y", "9223372036854775s999s"})
    void invalidDurations(String input) { assertEquals(-1, DurationUtil.parse(input)); }
    @ParameterizedTest @CsvSource({"-1,0s", "999,0s", "61000,1m", "3661000,1h 1m", "90061000,1d 1h"})
    void format(long input, String expected) { assertEquals(expected, DurationUtil.format(input)); }
    @ParameterizedTest @ValueSource(strings={"-s", "-s reason", "reason -s", "reason\t-s\nmore", "-s -s"})
    void silentFlags(String value) { assertTrue(CommandUtil.silent(value)); assertFalse(CommandUtil.silent(CommandUtil.cleanSilent(value))); }
    @Test void preservesReason() {
        assertFalse(CommandUtil.silent("not-s -silent"));
        assertEquals("not-s -silent", CommandUtil.cleanSilent("not-s -silent"));
        assertFalse(CommandUtil.silent(null)); assertEquals("", CommandUtil.cleanSilent(null));
    }
}
