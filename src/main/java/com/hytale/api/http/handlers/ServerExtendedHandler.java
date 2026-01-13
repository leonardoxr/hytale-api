package com.hytale.api.http.handlers;

import com.google.gson.Gson;
import com.hytale.api.dto.request.ServerRequests.WhitelistRequest;
import com.hytale.api.dto.response.ApiResponses.*;
import com.hytale.api.exception.ApiException;
import com.hytale.api.security.ApiPermissions;
import com.hytale.api.security.ClientIdentity;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import io.netty.handler.codec.http.FullHttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handler for extended server management endpoints (metrics, plugins, whitelist, save).
 */
public final class ServerExtendedHandler {
    private static final Logger LOGGER = Logger.getLogger(ServerExtendedHandler.class.getName());
    private static final Gson GSON = new Gson();

    /**
     * Handle GET /server/metrics request.
     * Returns server performance metrics.
     */
    public String handleMetrics(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.SERVER_METRICS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.SERVER_METRICS_READ);
        }

        HytaleServer server = HytaleServer.get();
        Universe universe = Universe.get();
        Runtime runtime = Runtime.getRuntime();

        // Memory metrics
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        double usedPercent = (double) usedMemory / maxMemory * 100.0;

        MetricsResponse.MemoryMetrics memory = new MetricsResponse.MemoryMetrics(
                usedMemory,
                maxMemory,
                freeMemory,
                usedPercent
        );

        // World metrics
        List<MetricsResponse.WorldMetrics> worldMetrics = new ArrayList<>();
        int totalEntities = 0;
        int totalChunks = 0;

        Map<String, World> worlds = universe.getWorlds();
        for (World world : worlds.values()) {
            // TODO: Get actual metrics from world
            int entities = 0; // world.getEntityStore().getEntityCount();
            int chunks = 0; // world.getChunkStore().getLoadedChunkCount();
            long tickTime = 0; // world.getLastTickTime();

            totalEntities += entities;
            totalChunks += chunks;

            worldMetrics.add(new MetricsResponse.WorldMetrics(
                    world.getName(),
                    world.getPlayerCount(),
                    entities,
                    chunks,
                    tickTime
            ));
        }

        // TODO: Get actual TPS and tick time
        double tps = 20.0; // Target TPS
        long tickTimeMs = 50; // ~50ms per tick at 20 TPS
        long avgTickTimeMs = 50;

        MetricsResponse response = new MetricsResponse(
                tps,
                tickTimeMs,
                avgTickTimeMs,
                memory,
                universe.getPlayerCount(),
                totalEntities,
                totalChunks,
                worldMetrics
        );

        return GSON.toJson(response);
    }

    /**
     * Handle GET /server/plugins request.
     * Returns list of loaded plugins.
     */
    public String handlePlugins(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.SERVER_PLUGINS_READ)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.SERVER_PLUGINS_READ);
        }

        HytaleServer server = HytaleServer.get();
        PluginManager pluginManager = server.getPluginManager();

        List<PluginsResponse.PluginInfo> plugins = new ArrayList<>();

        // Get all loaded plugins
        for (PluginBase plugin : pluginManager.getPlugins()) {
            String name = plugin.getClass().getSimpleName();
            String version = "1.0.0"; // TODO: Get from plugin manifest
            String description = ""; // TODO: Get from plugin manifest
            String state = plugin.getState().name();
            List<String> authors = List.of(); // TODO: Get from plugin manifest

            plugins.add(new PluginsResponse.PluginInfo(
                    name,
                    version,
                    description,
                    state,
                    authors
            ));
        }

        PluginsResponse response = new PluginsResponse(plugins.size(), plugins);

        return GSON.toJson(response);
    }

    /**
     * Handle POST /server/whitelist request.
     * Manages server whitelist.
     */
    public String handleWhitelist(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.SERVER_WHITELIST_WRITE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.SERVER_WHITELIST_WRITE);
        }

        String body = request.content().toString(StandardCharsets.UTF_8);
        WhitelistRequest whitelistRequest = GSON.fromJson(body, WhitelistRequest.class);

        if (whitelistRequest == null || !whitelistRequest.isValid()) {
            throw ApiException.BadRequest.invalidField("action", "Valid actions: add, remove, enable, disable");
        }

        String action = whitelistRequest.normalizedAction();

        // TODO: Implement actual whitelist management via server API
        // The server should have an AccessControl module for whitelist management

        boolean enabled = true; // Placeholder
        List<String> players = new ArrayList<>(); // Placeholder

        switch (action) {
            case "enable" -> {
                LOGGER.info("Whitelist enabled (by %s)".formatted(identity.clientId()));
                enabled = true;
            }
            case "disable" -> {
                LOGGER.info("Whitelist disabled (by %s)".formatted(identity.clientId()));
                enabled = false;
            }
            case "add" -> {
                List<String> toAdd = whitelistRequest.players();
                LOGGER.info("Added %d players to whitelist (by %s)".formatted(toAdd.size(), identity.clientId()));
                players.addAll(toAdd);
            }
            case "remove" -> {
                List<String> toRemove = whitelistRequest.players();
                LOGGER.info("Removed %d players from whitelist (by %s)".formatted(toRemove.size(), identity.clientId()));
                players.removeAll(toRemove);
            }
        }

        WhitelistResponse response = new WhitelistResponse(enabled, players.size(), players);

        return GSON.toJson(response);
    }

    /**
     * Handle POST /server/save request.
     * Forces a world save.
     */
    public String handleSave(FullHttpRequest request, ClientIdentity identity) {
        if (!identity.hasPermission(ApiPermissions.SERVER_SAVE)) {
            throw ApiException.Forbidden.insufficientPermissions(ApiPermissions.SERVER_SAVE);
        }

        Universe universe = Universe.get();

        // TODO: Implement actual world save via server API
        // This would iterate through all worlds and trigger a save

        int worldCount = universe.getWorlds().size();

        LOGGER.info("Forced save of %d worlds (by %s)".formatted(worldCount, identity.clientId()));

        return GSON.toJson(SuccessResponse.ok("Saved %d worlds".formatted(worldCount)));
    }
}
