package com.lezifx.trading.domain.enums;

public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    SUPPORT,
    PLAYER
    // MARKETER removed — marketer status is tracked via User.isMarketer flag, not role.
    // All marketer business logic branches on isMarketer; assigning a separate role was
    // never implemented and caused confusion with the working isMarketer approach.
}