package com.zaremate.punish.api;

import java.util.UUID;

/**
 * Immutable public representation of a punishment.
 */
public record Punishment(
        long id,
        PunishmentType type,
        boolean ipBased,
        UUID uuid,
        String name,
        String ip,
        String reason,
        String by,
        long at,
        long until,
        boolean active,
        boolean silent,
        String offense,
        int offenseNumber
) {
    public boolean isPermanent() {
        return until == 0;
    }

    public boolean isExpired() {
        return until > 0 && until <= System.currentTimeMillis();
    }

    public boolean isCurrent() {
        return active && !isExpired();
    }
}
