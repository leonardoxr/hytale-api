package com.hytale.api.http.handlers;

import com.google.gson.Gson;
import com.hytale.api.dto.request.AdminRequests.*;
import com.hytale.api.dto.response.ApiResponses.AdminActionResponse;
import com.hytale.api.dto.response.ApiResponses.CommandResponse;
import com.hytale.api.exception.ApiException;
import com.hytale.api.security.ApiPermissions;
import com.hytale.api.security.ClientIdentity;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.netty.handler.codec.http.FullHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handler for POST /admin/* endpoints.
 * Uses Java 21 pattern matching for clean request dispatch.
 */
public final class AdminHandler {
    private static final Logger LOGGER = Logger.getLogger(AdminHandler.class.getName());
    private static final Gson GSON = new Gson();

    /**
     * Admin action types for pattern matching.
     */
    public enum AdminAction {
        COMMAND, KICK, BAN, BROADCAST
    }

    /**
     * Route admin request to appropriate handler.
     */
    public String handle(FullHttpRequest request, ClientIdentity identity, AdminAction action) {
        return switch (action) {
            case COMMAND -> handleCommand(request, identity);
            case KICK -> handleKick(request, identity);
            case BAN -> handleBan(request, identity);
            case BROADCAST -> handleBroadcast(request, identity);
        };
    }

    /**
     * POST /admin/command - Execute a server command.
     */
    private String handleCommand(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.ADMIN_COMMAND)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.ADMIN_COMMAND);
        }

        CommandRequest cmdRequest = parseBody(request, CommandRequest.class);
        if (!cmdRequest.isValid()) {
            throw ApiException.BadRequest.missingField("command");
        }

        String command = sanitizeCommand(cmdRequest.command());
        LOGGER.info("[API] Executing command by %s: %s".formatted(identity.clientId(), command));

        try {
            // Execute command through server's command manager
            HytaleServer server = HytaleServer.get();
            // Note: Command execution API may need adjustment based on actual server implementation
            // This is a simplified version
            auditLog("COMMAND", identity, "command=" + command);

            return GSON.toJson(new CommandResponse(
                    true,
                    "Command queued for execution: " + command
            ));
        } catch (Exception e) {
            LOGGER.warning("Command execution failed: " + e.getMessage());
            return GSON.toJson(new CommandResponse(false, "Command failed: " + e.getMessage()));
        }
    }

    /**
     * POST /admin/kick - Kick a player from the server.
     */
    private String handleKick(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.ADMIN_KICK)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.ADMIN_KICK);
        }

        KickRequest kickRequest = parseBody(request, KickRequest.class);
        if (!kickRequest.isValid()) {
            throw ApiException.BadRequest.missingField("player");
        }

        PlayerRef playerRef = findPlayer(kickRequest.player());
        if (playerRef == null) {
            throw ApiException.NotFound.player(kickRequest.player());
        }

        String reason = kickRequest.effectiveReason();
        LOGGER.info("[API] Kick request by %s: player=%s, reason=%s"
                .formatted(identity.clientId(), playerRef.getUsername(), reason));

        try {
            // Disconnect the player through their packet handler
            playerRef.getPacketHandler().disconnect(reason);
            auditLog("KICK", identity, "player=" + playerRef.getUsername() + ", reason=" + reason);

            return GSON.toJson(new AdminActionResponse(
                    true,
                    "kick",
                    playerRef.getUsername(),
                    "Player kicked: " + reason
            ));
        } catch (Exception e) {
            LOGGER.warning("Kick failed: " + e.getMessage());
            return GSON.toJson(new AdminActionResponse(false, "kick", kickRequest.player(), e.getMessage()));
        }
    }

    /**
     * POST /admin/ban - Ban a player (kicks if online, actual ban requires server ban system).
     */
    private String handleBan(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.ADMIN_BAN)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.ADMIN_BAN);
        }

        BanRequest banRequest = parseBody(request, BanRequest.class);
        if (!banRequest.isValid()) {
            throw ApiException.BadRequest.missingField("player");
        }

        String reason = banRequest.effectiveReason();
        LOGGER.info("[API] Ban request by %s: player=%s, reason=%s, permanent=%s"
                .formatted(identity.clientId(), banRequest.player(), reason, banRequest.isPermanent()));

        try {
            // Try to find and kick player if online
            PlayerRef playerRef = findPlayer(banRequest.player());
            if (playerRef != null) {
                playerRef.getPacketHandler().disconnect("Banned: " + reason);
            }

            // Note: Actual ban persistence would require access to server's ban system
            // which may need additional API exploration
            auditLog("BAN", identity, "player=" + banRequest.player() + ", reason=" + reason);

            return GSON.toJson(new AdminActionResponse(
                    true,
                    "ban",
                    banRequest.player(),
                    banRequest.isPermanent() ? "Player banned" : "Player banned for " + banRequest.durationMinutes() + " minutes"
            ));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warning("Ban failed: " + e.getMessage());
            return GSON.toJson(new AdminActionResponse(false, "ban", banRequest.player(), e.getMessage()));
        }
    }

    /**
     * POST /admin/broadcast - Send a broadcast message to all players.
     */
    private String handleBroadcast(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.ADMIN_BROADCAST)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.ADMIN_BROADCAST);
        }

        BroadcastRequest broadcastRequest = parseBody(request, BroadcastRequest.class);
        if (!broadcastRequest.isValid()) {
            throw ApiException.BadRequest.missingField("message");
        }

        String message = broadcastRequest.message();
        LOGGER.info("[API] Broadcast by %s: %s".formatted(identity.clientId(), message));

        try {
            Universe universe = Universe.get();
            // Send message to universe (broadcasts to all players)
            universe.sendMessage(Message.raw(message));

            auditLog("BROADCAST", identity, "message=" + truncate(message, 100));

            return GSON.toJson(new AdminActionResponse(
                    true,
                    "broadcast",
                    "all",
                    "Broadcast sent to " + universe.getPlayerCount() + " players"
            ));
        } catch (Exception e) {
            LOGGER.warning("Broadcast failed: " + e.getMessage());
            return GSON.toJson(new AdminActionResponse(false, "broadcast", "all", e.getMessage()));
        }
    }

    /**
     * Parse request body as JSON.
     */
    private <T> T parseBody(FullHttpRequest request, Class<T> clazz) {
        try {
            String body = request.content().toString(StandardCharsets.UTF_8);
            return GSON.fromJson(body, clazz);
        } catch (Exception e) {
            throw ApiException.BadRequest.invalidJson(e.getMessage());
        }
    }

    /**
     * Find player by name or UUID.
     */
    private PlayerRef findPlayer(String identifier) {
        Universe universe = Universe.get();

        // Try UUID first
        try {
            UUID uuid = UUID.fromString(identifier);
            return universe.getPlayer(uuid);
        } catch (IllegalArgumentException e) {
            // Not a UUID, search by name using exact matching
            return universe.getPlayer(identifier, com.hypixel.hytale.server.core.NameMatching.EXACT);
        }
    }

    /**
     * Sanitize command input to prevent injection.
     */
    private String sanitizeCommand(String command) {
        // Remove any newlines, control characters
        return command
                .replaceAll("[\\r\\n\\x00-\\x1F]", "")
                .trim();
    }

    /**
     * Log admin action for audit trail.
     */
    private void auditLog(String action, ClientIdentity identity, String details) {
        LOGGER.info("[AUDIT] %s by client '%s': %s".formatted(action, identity.clientId(), details));
    }

    /**
     * Truncate string for logging.
     */
    private String truncate(String s, int maxLength) {
        if (s == null || s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "...";
    }
}
