package com.hytale.api.security;

/**
 * Permission node constants for the API.
 * Follows hierarchical permission structure with wildcards.
 */
public final class ApiPermissions {
    private ApiPermissions() {}

    // Root wildcard
    public static final String ALL = "api.*";

    // Status/Server Info
    public static final String STATUS_READ = "api.status.read";

    // Player Management
    public static final String PLAYERS_READ = "api.players.read";
    public static final String PLAYERS_ALL = "api.players.*";

    // World Management
    public static final String WORLDS_READ = "api.worlds.read";
    public static final String WORLDS_ALL = "api.worlds.*";

    // Admin Operations
    public static final String ADMIN_ALL = "api.admin.*";
    public static final String ADMIN_COMMAND = "api.admin.command";
    public static final String ADMIN_KICK = "api.admin.kick";
    public static final String ADMIN_BAN = "api.admin.ban";
    public static final String ADMIN_BROADCAST = "api.admin.broadcast";

    // WebSocket
    public static final String WEBSOCKET_CONNECT = "api.websocket.connect";
    public static final String WEBSOCKET_SUBSCRIBE_PLAYERS = "api.websocket.subscribe.players";
    public static final String WEBSOCKET_SUBSCRIBE_CHAT = "api.websocket.subscribe.chat";
    public static final String WEBSOCKET_SUBSCRIBE_STATUS = "api.websocket.subscribe.status";
    public static final String WEBSOCKET_SUBSCRIBE_ALL = "api.websocket.subscribe.*";

    /**
     * Check if a permission string matches a required permission.
     * Supports wildcards like "api.*" and "api.admin.*"
     */
    public static boolean matches(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        if (granted.equals(required)) {
            return true;
        }
        if (granted.equals("*") || granted.equals("api.*")) {
            return required.startsWith("api.");
        }
        if (granted.endsWith(".*")) {
            String prefix = granted.substring(0, granted.length() - 1);
            return required.startsWith(prefix);
        }
        return false;
    }
}
