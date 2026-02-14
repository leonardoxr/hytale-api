package com.hytale.api.websocket;

import com.google.gson.Gson;
import com.hytale.api.config.ApiConfig;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DeathEvent;
import com.hypixel.hytale.server.core.event.events.inventory.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.inventory.CraftRecipeEvent;
import com.hypixel.hytale.server.core.event.events.inventory.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.permission.PlayerPermissionChangeEvent;
import com.hypixel.hytale.server.core.event.events.permission.PlayerGroupEvent;
import com.hypixel.hytale.server.core.event.events.world.DiscoverZoneEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Bridges Hytale server events to WebSocket broadcasts.
 * Uses virtual threads for periodic status broadcasts.
 *
 * <p>Updated for SDK 2.0 with support for player interaction, world change,
 * block break/place (ECS), death/damage (ECS), inventory, crafting, item drop,
 * permission change, group change, and zone discovery events.</p>
 */
public final class EventBroadcaster {
    private static final Logger LOGGER = Logger.getLogger(EventBroadcaster.class.getName());
    private static final Gson GSON = new Gson();

    private final ApiConfig config;
    private final WebSocketSessionManager sessionManager;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> statusBroadcastTask;

    public EventBroadcaster(ApiConfig config, WebSocketSessionManager sessionManager) {
        this.config = config;
        this.sessionManager = sessionManager;
        // Use virtual threads for efficiency (Java 21+)
        this.scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    }

    /**
     * Register all event listeners.
     */
    public void registerEvents(EventRegistry eventRegistry) {
        // Player connection events
        eventRegistry.registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Player chat event
        eventRegistry.registerAsyncGlobal(PlayerChatEvent.class, future -> {
            future.thenAccept(this::onPlayerChat);
        });

        // Game mode change event
        eventRegistry.registerGlobal(ChangeGameModeEvent.class, this::onPlayerGameModeChange);

        // Entity events
        eventRegistry.registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        // Player interaction and world change events (SDK 2.0)
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerWorldEntry);
        eventRegistry.registerGlobal(DrainPlayerFromWorldEvent.class, this::onPlayerWorldExit);

        // Inventory events (SDK 2.0)
        eventRegistry.registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        eventRegistry.registerGlobal(CraftRecipeEvent.class, this::onCraftRecipe);
        eventRegistry.registerGlobal(DropItemEvent.class, this::onDropItem);

        // Permission events (SDK 2.0)
        eventRegistry.registerGlobal(PlayerPermissionChangeEvent.class, this::onPermissionChange);
        eventRegistry.registerGlobal(PlayerGroupEvent.class, this::onGroupChange);

        // Zone events (SDK 2.0)
        eventRegistry.registerGlobal(DiscoverZoneEvent.class, this::onZoneDiscovery);

        // ECS Events - block break/place and death/damage
        // Note: ECS events may require EntityEventSystem registration in some SDK versions.
        // These use registerGlobal as a fallback; if the SDK requires ECS-style registration,
        // they should be adapted to extend EntityEventSystem<EntityStore, EventType>.
        eventRegistry.registerGlobal(BreakBlockEvent.class, this::onBlockBreak);
        eventRegistry.registerGlobal(PlaceBlockEvent.class, this::onBlockPlace);
        eventRegistry.registerGlobal(DeathEvent.class, this::onEntityDeath);
        eventRegistry.registerGlobal(DamageEvent.class, this::onEntityDamage);

        LOGGER.info("Event listeners registered for WebSocket broadcast");

        // Start periodic status broadcast
        startStatusBroadcast();
    }

    /**
     * Start periodic server status broadcasts.
     */
    private void startStatusBroadcast() {
        int intervalSeconds = config.websocket().statusBroadcastIntervalSeconds();
        if (intervalSeconds <= 0) return;

        statusBroadcastTask = scheduler.scheduleAtFixedRate(
                this::broadcastServerStatus,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        LOGGER.info("Status broadcast started with %d second interval".formatted(intervalSeconds));
    }

    /**
     * Handle player connection event.
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        var playerRef = event.getPlayerRef();

        String payload = """
                {"uuid":"%s","name":"%s"}"""
                .formatted(
                        playerRef.getUuid(),
                        escapeJson(playerRef.getUsername())
                );

        sessionManager.broadcast("player.connect", payload);
    }

    /**
     * Handle player ready event (fully joined).
     */
    @SuppressWarnings("removal") // Entity.getUuid() deprecated but no replacement available yet
    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        var world = player.getWorld();

        String payload = """
                {"uuid":"%s","name":"%s","world":"%s"}"""
                .formatted(
                        player.getUuid(),
                        "Player", // Player entity may not have direct name access
                        world != null ? escapeJson(world.getName()) : "unknown"
                );

        sessionManager.broadcast("player.join", payload);
    }

    /**
     * Handle player disconnect event.
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        var playerRef = event.getPlayerRef();
        var reason = event.getDisconnectReason();

        String payload = """
                {"uuid":"%s","name":"%s","reason":"%s"}"""
                .formatted(
                        playerRef.getUuid(),
                        escapeJson(playerRef.getUsername()),
                        escapeJson(reason != null ? reason.toString() : "DISCONNECTED")
                );

        sessionManager.broadcast("player.leave", payload);
    }

    /**
     * Broadcast server status to all subscribed clients.
     */
    private void broadcastServerStatus() {
        try {
            if (sessionManager.getSessionCount() == 0) return;

            HytaleServer server = HytaleServer.get();
            Universe universe = Universe.get();
            Runtime runtime = Runtime.getRuntime();

            long uptime = System.currentTimeMillis() - server.getBoot().toEpochMilli();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();

            String payload = """
                    {"name":"%s","players":%d,"maxPlayers":%d,"uptime":%d,"memory":{"used":%d,"max":%d}}"""
                    .formatted(
                            escapeJson(server.getServerName()),
                            universe.getPlayerCount(),
                            server.getConfig().getMaxPlayers(),
                            uptime,
                            usedMemory,
                            runtime.maxMemory()
                    );

            sessionManager.broadcast("server.status", payload);

        } catch (Exception e) {
            LOGGER.warning("Status broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle player chat event.
     */
    private void onPlayerChat(PlayerChatEvent event) {
        var playerRef = event.getSender();
        String content = event.getContent();

        String payload = """
                {"uuid":"%s","name":"%s","message":"%s"}"""
                .formatted(
                        playerRef.getUuid(),
                        escapeJson(playerRef.getUsername()),
                        escapeJson(content)
                );

        sessionManager.broadcast("player.chat", payload);
    }

    /**
     * Handle game mode change event.
     */
    private void onPlayerGameModeChange(ChangeGameModeEvent event) {
        // TODO: Extract player info and game mode from event
        String payload = """
                {"gameMode":"%s"}"""
                .formatted(escapeJson(event.getGameMode().name()));

        sessionManager.broadcast("player.gamemode", payload);
    }

    /**
     * Handle entity remove event.
     */
    @SuppressWarnings("removal")
    private void onEntityRemove(EntityRemoveEvent event) {
        var entity = event.getEntity();

        String payload = """
                {"uuid":"%s","type":"%s"}"""
                .formatted(
                        entity.getUuid(),
                        entity.getClass().getSimpleName()
                );

        sessionManager.broadcast("entity.remove", payload);
    }

    /**
     * Handle player interaction event (SDK 2.0).
     */
    @SuppressWarnings("removal")
    private void onPlayerInteract(PlayerInteractEvent event) {
        try {
            var player = event.getPlayer();
            String payload = """
                    {"uuid":"%s","actionType":"%s","hasItem":%b,"hasTarget":%b}"""
                    .formatted(
                            player.getUuid(),
                            escapeJson(event.getActionType().name()),
                            event.getItemInHand() != null,
                            event.getTargetEntity() != null || event.getTargetBlock() != null
                    );
            sessionManager.broadcast("player.interact", payload);
        } catch (Exception e) {
            LOGGER.fine("Player interact event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle player entering a world (SDK 2.0).
     */
    private void onPlayerWorldEntry(AddPlayerToWorldEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            var world = event.getWorld();
            String payload = """
                    {"uuid":"%s","name":"%s","world":"%s"}"""
                    .formatted(
                            playerRef.getUuid(),
                            escapeJson(playerRef.getUsername()),
                            world != null ? escapeJson(world.getName()) : "unknown"
                    );
            sessionManager.broadcast("player.world_change", payload);
        } catch (Exception e) {
            LOGGER.fine("Player world entry event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle player exiting a world (SDK 2.0).
     */
    private void onPlayerWorldExit(DrainPlayerFromWorldEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            String payload = """
                    {"uuid":"%s","name":"%s","action":"exit"}"""
                    .formatted(
                            playerRef.getUuid(),
                            escapeJson(playerRef.getUsername())
                    );
            sessionManager.broadcast("player.world_change", payload);
        } catch (Exception e) {
            LOGGER.fine("Player world exit event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle block break event (SDK 2.0 - ECS event).
     */
    private void onBlockBreak(BreakBlockEvent event) {
        try {
            String payload = """
                    {"action":"break"}""";
            sessionManager.broadcast("block.break", payload);
        } catch (Exception e) {
            LOGGER.fine("Block break event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle block place event (SDK 2.0 - ECS event).
     */
    private void onBlockPlace(PlaceBlockEvent event) {
        try {
            String payload = """
                    {"action":"place"}""";
            sessionManager.broadcast("block.place", payload);
        } catch (Exception e) {
            LOGGER.fine("Block place event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle entity death event (SDK 2.0 - ECS event).
     */
    private void onEntityDeath(DeathEvent event) {
        try {
            String payload = """
                    {"event":"death"}""";
            sessionManager.broadcast("entity.death", payload);
        } catch (Exception e) {
            LOGGER.fine("Death event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle entity damage event (SDK 2.0 - ECS event).
     */
    private void onEntityDamage(DamageEvent event) {
        try {
            String payload = """
                    {"event":"damage"}""";
            sessionManager.broadcast("entity.damage", payload);
        } catch (Exception e) {
            LOGGER.fine("Damage event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle inventory change event (SDK 2.0).
     */
    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        try {
            String payload = """
                    {"event":"inventory_change"}""";
            sessionManager.broadcast("inventory.change", payload);
        } catch (Exception e) {
            LOGGER.fine("Inventory change event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle crafting event (SDK 2.0).
     */
    private void onCraftRecipe(CraftRecipeEvent event) {
        try {
            String payload = """
                    {"event":"craft"}""";
            sessionManager.broadcast("inventory.craft", payload);
        } catch (Exception e) {
            LOGGER.fine("Craft event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle item drop event (SDK 2.0).
     */
    private void onDropItem(DropItemEvent event) {
        try {
            String payload = """
                    {"event":"drop"}""";
            sessionManager.broadcast("inventory.drop", payload);
        } catch (Exception e) {
            LOGGER.fine("Drop item event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle permission change event (SDK 2.0).
     */
    private void onPermissionChange(PlayerPermissionChangeEvent event) {
        try {
            String payload = """
                    {"event":"permission_change"}""";
            sessionManager.broadcast("player.permission", payload);
        } catch (Exception e) {
            LOGGER.fine("Permission change event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle group change event (SDK 2.0).
     */
    private void onGroupChange(PlayerGroupEvent event) {
        try {
            String payload = """
                    {"event":"group_change"}""";
            sessionManager.broadcast("player.group", payload);
        } catch (Exception e) {
            LOGGER.fine("Group change event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Handle zone discovery event (SDK 2.0).
     */
    private void onZoneDiscovery(DiscoverZoneEvent event) {
        try {
            String payload = """
                    {"event":"zone_discovery"}""";
            sessionManager.broadcast("world.zone_discovery", payload);
        } catch (Exception e) {
            LOGGER.fine("Zone discovery event broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Shutdown the broadcaster.
     */
    public void shutdown() {
        if (statusBroadcastTask != null) {
            statusBroadcastTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Event broadcaster shutdown complete");
    }

    // Utility methods

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
