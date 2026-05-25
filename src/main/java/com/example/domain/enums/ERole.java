package com.example.domain.enums;

public enum ERole {
    ROLE_USER,
    ROLE_ADMIN,
    ROLE_MODERATOR,
    ROLE_SUPER_ADMIN;

    public String getRoleName() {
        return this.name().replace("ROLE_", "");
    }

    public static ERole fromString(String role) {
        if (role == null) {
            return ROLE_USER;
        }

        String normalizedRole = role.toUpperCase();
        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }

        try {
            return ERole.valueOf(normalizedRole);
        } catch (IllegalArgumentException e) {
            return ROLE_USER;
        }
    }
}