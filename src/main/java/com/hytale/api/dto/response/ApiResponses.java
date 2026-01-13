package com.hytale.api.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTOs for API endpoints.
 * All responses are JSON-serializable records.
 */
public final class ApiResponses {
    private ApiResponses() {}

    /**
     * Health check response.
     */
    public record HealthResponse(String status, long timestamp) {
        public static HealthResponse ok() {
            return new HealthResponse("ok", System.currentTimeMillis());
        }
    }

    /**
     * Server status response.
     */
    public record StatusResponse(
            String name,
            String motd,
            int playerCount,
            int maxPlayers,
            long uptime,
            MemoryInfo memory,
            boolean online
    ) {
        public record MemoryInfo(long used, long max, long free) {}
    }

    /**
     * Player list response.
     */
    public record PlayersResponse(
            int count,
            List<PlayerInfo> players
    ) {
        public record PlayerInfo(
                UUID uuid,
                String name,
                String world,
                Position position,
                long connectedAt
        ) {
            public record Position(double x, double y, double z) {}
        }
    }

    /**
     * Single player details response.
     */
    public record PlayerDetailResponse(
            UUID uuid,
            String name,
            String world,
            PlayersResponse.PlayerInfo.Position position,
            long connectedAt,
            Stats stats,
            String gameMode
    ) {
        public record Stats(int health, int mana, int stamina) {}
    }

    /**
     * World list response.
     */
    public record WorldsResponse(
            int count,
            List<WorldInfo> worlds
    ) {
        public record WorldInfo(
                UUID uuid,
                String name,
                int playerCount,
                String type
        ) {}
    }

    /**
     * Single world details response.
     */
    public record WorldDetailResponse(
            UUID uuid,
            String name,
            int playerCount,
            String type,
            List<String> players,
            SpawnPosition spawn
    ) {
        public record SpawnPosition(double x, double y, double z) {}
    }

    /**
     * Command execution response.
     */
    public record CommandResponse(
            boolean success,
            String output
    ) {}

    /**
     * Admin action response (kick, ban, broadcast).
     */
    public record AdminActionResponse(
            boolean success,
            String action,
            String target,
            String message
    ) {}

    /**
     * Generic success response.
     */
    public record SuccessResponse(boolean success, String message) {
        public static SuccessResponse ok(String message) {
            return new SuccessResponse(true, message);
        }
    }

    /**
     * Detailed server statistics response.
     */
    public record ServerStatsResponse(
            String name,
            long uptime,
            int playerCount,
            int maxPlayers,
            int worldCount,
            int totalEntities,
            int totalChunksLoaded,
            MemoryStats memory,
            List<WorldStats> worlds
    ) {
        public record MemoryStats(
                long used,
                long max,
                long free,
                double usedPercent
        ) {}

        public record WorldStats(
                String name,
                int playerCount,
                int entityCount,
                int chunksLoaded,
                int chunksGenerated
        ) {}
    }

    /**
     * Detailed world statistics response.
     */
    public record WorldStatsResponse(
            String name,
            int playerCount,
            int entityCount,
            ChunkStats chunks,
            List<EntityTypeCount> entityTypes
    ) {
        public record ChunkStats(
                int loaded,
                int generated,
                int total
        ) {}

        public record EntityTypeCount(
                String type,
                int count
        ) {}
    }
}
