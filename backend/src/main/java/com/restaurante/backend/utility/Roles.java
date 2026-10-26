package com.restaurante.backend.utility;

public final class Roles {
    public static final String MOZO = "MOZO";
    public static final String COCINERO = "COCINERO";
    public static final String ADMIN = "ADMIN";

    public static String authority(String role) {
        String normalizedRole = role.toUpperCase(java.util.Locale.ROOT);
        return normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole;
    }

    private Roles() {
    }
}