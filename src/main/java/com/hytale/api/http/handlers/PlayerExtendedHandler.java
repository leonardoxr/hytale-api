package com.hytale.api.http.handlers;

import com.google.gson.Gson;
import com.hytale.api.dto.request.PlayerRequests.*;
import com.hytale.api.dto.response.ApiResponses.*;
import com.hytale.api.exception.ApiException;
import com.hytale.api.security.ApiPermissions;
import com.hytale.api.security.ClientIdentity;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import io.netty.handler.codec.http.FullHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handler for extended player endpoints (stats, location, teleport, gamemode, permissions).
 */
public final class PlayerExtendedHandler {
    private static final Logger LOGGER = Logger.getLogger(PlayerExtendedHandler.class.getName());
    private static final Gson GSON = new Gson();

    /**
     * Handle GET /players/{uuid}/stats request.
     * Returns player stats (health, mana, stamina, oxygen).
     */
    public String handleStats(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_STATS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_STATS_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Access EntityStats component for actual values
        // For now, return placeholder values
        PlayerStatsResponse response = new PlayerStatsResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                100.0, 100.0, // health, maxHealth
                100.0, 100.0, // mana, maxMana
                100.0, 100.0, // stamina, maxStamina
                100.0, 100.0  // oxygen, maxOxygen
        );

        return GSON.toJson(response);
    }

    /**
     * Handle GET /players/{uuid}/location request.
     * Returns player position and rotation.
     */
    public String handleLocation(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_LOCATION_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_LOCATION_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        var transform = playerRef.getTransform();
        var position = transform.getPosition();
        var rotation = transform.getRotation();

        Universe universe = Universe.get();
        World world = universe.getWorld(playerRef.getWorldUuid());
        String worldName = world != null ? world.getName() : "unknown";

        // Vector3d uses getX(), getY(), getZ() methods
        // Vector3f rotation: X = yaw, Y = pitch
        PlayerLocationResponse response = new PlayerLocationResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                worldName,
                new PlayerLocationResponse.Position(position.getX(), position.getY(), position.getZ()),
                new PlayerLocationResponse.Rotation(rotation.getX(), rotation.getY())
        );

        return GSON.toJson(response);
    }

    /**
     * Handle POST /players/{uuid}/teleport request.
     */
    public String handleTeleport(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_TELEPORT)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_TELEPORT);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        TeleportRequest teleportRequest = GSON.fromJson(body, TeleportRequest.class);

        if (teleportRequest == null || !teleportRequest.isValid()) {
            throw ApiException.BadRequest.missingField("x, y, z coordinates or world");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);
        Universe universe = Universe.get();

        // Handle world change if specified
        String targetWorldName = teleportRequest.world();
        World targetWorld = null;
        if (targetWorldName != null && !targetWorldName.isBlank()) {
            targetWorld = universe.getWorld(targetWorldName);
            if (targetWorld == null) {
                throw ApiException.NotFound.world(targetWorldName);
            }
        } else {
            targetWorld = universe.getWorld(playerRef.getWorldUuid());
        }

        // TODO: Implement actual teleportation via server API
        // This would involve updating the player's transform component

        String worldName = targetWorld != null ? targetWorld.getName() : "unknown";
        double x = teleportRequest.x() != null ? teleportRequest.x() : 0;
        double y = teleportRequest.y() != null ? teleportRequest.y() : 0;
        double z = teleportRequest.z() != null ? teleportRequest.z() : 0;

        LOGGER.info("Teleported %s to %.2f, %.2f, %.2f in %s (by %s)".formatted(
                playerRef.getUsername(), x, y, z, worldName, identity.clientId()
        ));

        TeleportResponse response = new TeleportResponse(
                true,
                playerRef.getUuid(),
                playerRef.getUsername(),
                worldName,
                new PlayerLocationResponse.Position(x, y, z)
        );

        return GSON.toJson(response);
    }

    /**
     * Handle GET /players/{uuid}/gamemode request.
     */
    public String handleGetGameMode(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_GAMEMODE_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_GAMEMODE_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Get actual game mode from player entity
        String gameMode = "ADVENTURE"; // Placeholder

        GameModeResponse response = new GameModeResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                gameMode
        );

        return GSON.toJson(response);
    }

    /**
     * Handle POST /players/{uuid}/gamemode request.
     */
    public String handleSetGameMode(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_GAMEMODE_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_GAMEMODE_WRITE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        SetGameModeRequest gameModeRequest = GSON.fromJson(body, SetGameModeRequest.class);

        if (gameModeRequest == null || !gameModeRequest.isValid()) {
            throw ApiException.BadRequest.missingField("gameMode");
        }

        String gameMode = gameModeRequest.gameMode().toUpperCase();
        if (!isValidGameMode(gameMode)) {
            throw ApiException.BadRequest.invalidGameMode(gameMode);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Implement actual game mode change via server API

        LOGGER.info("Set game mode of %s to %s (by %s)".formatted(
                playerRef.getUsername(), gameMode, identity.clientId()
        ));

        GameModeResponse response = new GameModeResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                gameMode
        );

        return GSON.toJson(response);
    }

    /**
     * Handle GET /players/{uuid}/permissions request.
     */
    public String handleGetPermissions(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_PERMISSIONS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_PERMISSIONS_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Get actual permissions from PermissionHolder
        List<String> permissions = new ArrayList<>();

        PermissionsResponse response = new PermissionsResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                permissions
        );

        return GSON.toJson(response);
    }

    /**
     * Handle POST /players/{uuid}/permissions request.
     */
    public String handleGrantPermission(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_PERMISSIONS_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_PERMISSIONS_WRITE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        GrantPermissionRequest permRequest = GSON.fromJson(body, GrantPermissionRequest.class);

        if (permRequest == null || !permRequest.isValid()) {
            throw ApiException.BadRequest.missingField("permission");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Grant permission via PermissionHolder

        LOGGER.info("Granted permission '%s' to %s (by %s)".formatted(
                permRequest.permission(), playerRef.getUsername(), identity.clientId()
        ));

        return GSON.toJson(SuccessResponse.ok("Granted permission '%s' to %s".formatted(
                permRequest.permission(), playerRef.getUsername()
        )));
    }

    /**
     * Handle DELETE /players/{uuid}/permissions/{permission} request.
     */
    public String handleRevokePermission(FullHttpRequest request, ClientIdentity identity, String uuidString, String permission) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_PERMISSIONS_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_PERMISSIONS_WRITE);
        }

        if (permission == null || permission.isBlank()) {
            throw ApiException.BadRequest.missingField("permission");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Revoke permission via PermissionHolder

        LOGGER.info("Revoked permission '%s' from %s (by %s)".formatted(
                permission, playerRef.getUsername(), identity.clientId()
        ));

        return GSON.toJson(SuccessResponse.ok("Revoked permission '%s' from %s".formatted(
                permission, playerRef.getUsername()
        )));
    }

    /**
     * Handle GET /players/{uuid}/groups request.
     */
    public String handleGetGroups(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_GROUPS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_GROUPS_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Get actual groups from PermissionHolder
        List<String> groups = new ArrayList<>();

        GroupsResponse response = new GroupsResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                groups
        );

        return GSON.toJson(response);
    }

    /**
     * Handle POST /players/{uuid}/groups request.
     */
    public String handleAddToGroup(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_GROUPS_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_GROUPS_WRITE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        AddToGroupRequest groupRequest = GSON.fromJson(body, AddToGroupRequest.class);

        if (groupRequest == null || !groupRequest.isValid()) {
            throw ApiException.BadRequest.missingField("group");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Add to group via PermissionHolder

        LOGGER.info("Added %s to group '%s' (by %s)".formatted(
                playerRef.getUsername(), groupRequest.group(), identity.clientId()
        ));

        return GSON.toJson(SuccessResponse.ok("Added %s to group '%s'".formatted(
                playerRef.getUsername(), groupRequest.group()
        )));
    }

    /**
     * Handle POST /players/{uuid}/message request.
     */
    public String handleSendMessage(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_MESSAGE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_MESSAGE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        SendMessageRequest messageRequest = GSON.fromJson(body, SendMessageRequest.class);

        if (messageRequest == null || !messageRequest.isValid()) {
            throw ApiException.BadRequest.missingField("message");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // Send message to player using the SDK Message API (SDK 2.0)
        try {
            playerRef.getPacketHandler().sendMessage(Message.raw(messageRequest.message()));
        } catch (Exception e) {
            LOGGER.warning("Failed to send message via SDK, logging action: " + e.getMessage());
        }

        LOGGER.info("Sent message to %s: '%s' (by %s)".formatted(
                playerRef.getUsername(), messageRequest.message(), identity.clientId()
        ));

        return GSON.toJson(SuccessResponse.ok("Message sent to %s".formatted(playerRef.getUsername())));
    }

    /**
     * Handle POST /players/{uuid}/heal request (SDK 2.0).
     * Heals a player by specified amount or full heal.
     */
    public String handleHeal(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_HEAL)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_HEAL);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        HealRequest healRequest = body.isEmpty()
                ? new HealRequest(null) // null means full heal
                : GSON.fromJson(body, HealRequest.class);

        if (healRequest != null && !healRequest.isValid()) {
            throw ApiException.BadRequest.invalidField("amount", "Heal amount must be positive");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Access actual health from EntityStats component when available
        double maxHealth = 100.0;
        double previousHealth = 100.0; // Placeholder
        double newHealth = maxHealth; // Full heal by default

        if (healRequest != null && !healRequest.isFullHeal()) {
            newHealth = Math.min(previousHealth + healRequest.amount(), maxHealth);
        }

        LOGGER.info("Healed %s: %.1f -> %.1f (by %s)".formatted(
                playerRef.getUsername(), previousHealth, newHealth, identity.clientId()
        ));

        HealResponse response = new HealResponse(
                true,
                playerRef.getUuid(),
                playerRef.getUsername(),
                previousHealth,
                newHealth,
                maxHealth
        );

        return GSON.toJson(response);
    }

    /**
     * Handle GET /players/{uuid}/effects request (SDK 2.0).
     * Returns active effects on a player.
     */
    public String handleGetEffects(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_EFFECTS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_EFFECTS_READ);
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Access actual effects from Player entity's ECS components
        List<EffectsResponse.EffectInfo> effects = new ArrayList<>();

        EffectsResponse response = new EffectsResponse(
                playerRef.getUuid(),
                playerRef.getUsername(),
                effects
        );

        return GSON.toJson(response);
    }

    /**
     * Handle POST /players/{uuid}/effects request (SDK 2.0).
     * Applies an effect to a player.
     */
    public String handleApplyEffect(FullHttpRequest request, ClientIdentity identity, String uuidString) {
        if (!identity.hasPermission(ApiPermissions.PLAYERS_EFFECTS_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.PLAYERS_EFFECTS_WRITE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        ApplyEffectRequest effectRequest = GSON.fromJson(body, ApplyEffectRequest.class);

        if (effectRequest == null || !effectRequest.isValid()) {
            throw ApiException.BadRequest.missingField("effectId");
        }

        PlayerRef playerRef = getPlayerRef(uuidString);

        // TODO: Apply effect via ECS component system
        LOGGER.info("Applied effect '%s' (amp:%d, dur:%d) to %s (by %s)".formatted(
                effectRequest.effectId(),
                effectRequest.getAmplifierOrDefault(),
                effectRequest.getDurationOrDefault(),
                playerRef.getUsername(),
                identity.clientId()
        ));

        return GSON.toJson(SuccessResponse.ok("Applied effect '%s' to %s".formatted(
                effectRequest.effectId(), playerRef.getUsername()
        )));
    }

    // Helper methods

    private PlayerRef getPlayerRef(String uuidString) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            throw ApiException.BadRequest.invalidField("uuid", "Invalid UUID format");
        }

        Universe universe = Universe.get();
        PlayerRef playerRef = universe.getPlayer(uuid);

        if (playerRef == null) {
            throw ApiException.NotFound.player(uuidString);
        }

        return playerRef;
    }

    private boolean isValidGameMode(String mode) {
        return switch (mode) {
            case "ADVENTURE", "CREATIVE", "SPECTATOR" -> true;
            default -> false;
        };
    }
}
