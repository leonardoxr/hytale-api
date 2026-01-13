package com.hytale.api;

import com.hytale.api.config.ApiConfig;
import com.hytale.api.http.ApiChannelInitializer;
import com.hytale.api.security.TokenGenerator;
import com.hytale.api.websocket.EventBroadcaster;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hytale REST & WebSocket API Plugin.
 *
 * Provides secure HTTP API endpoints and real-time WebSocket events
 * for server management and monitoring.
 *
 * Features:
 * - JWT authentication
 * - Rate limiting
 * - WebSocket real-time events
 * - TLS support (optional)
 * - Permission-based access control
 *
 * @author HytaleAPI Team
 * @version 1.0.0
 */
public final class ApiPlugin extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger(ApiPlugin.class.getName());

    private ApiConfig config;
    private TokenGenerator tokenGenerator;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private ApiChannelInitializer channelInitializer;
    private EventBroadcaster eventBroadcaster;

    public ApiPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.info("HytaleAPI plugin initializing...");

        try {
            // Load configuration
            Path configPath = getDataDirectory().resolve("config.json");
            config = ApiConfig.load(configPath);

            if (!config.enabled()) {
                LOGGER.info("API is disabled in configuration. Skipping setup.");
                return;
            }

            // Initialize JWT token generator
            Path keyPath = getDataDirectory().resolve(config.jwt().rsaKeyPath());
            tokenGenerator = new TokenGenerator(config.jwt(), keyPath);

            // Initialize channel initializer (contains all handlers)
            channelInitializer = new ApiChannelInitializer(config, tokenGenerator, getDataDirectory());

            // Initialize event broadcaster for WebSocket
            if (config.websocket().enabled()) {
                eventBroadcaster = new EventBroadcaster(config, channelInitializer.getWebSocketSessionManager());
                eventBroadcaster.registerEvents(getEventRegistry());
            }

            LOGGER.info("HytaleAPI plugin setup complete");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize API plugin", e);
        }
    }

    @Override
    protected void start() {
        if (config == null || !config.enabled()) {
            LOGGER.info("API is disabled. Not starting HTTP server.");
            return;
        }

        LOGGER.info("Starting HytaleAPI HTTP server...");

        try {
            // Create Netty event loop groups
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            // Configure and start server
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(channelInitializer)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            // Bind to configured address and port
            serverChannel = bootstrap.bind(config.bindAddress(), config.port())
                    .sync()
                    .channel();

            String protocol = config.tls().enabled() ? "https" : "http";
            LOGGER.info("HytaleAPI server started on %s://%s:%d"
                    .formatted(protocol, config.bindAddress(), config.port()));

            // Log enabled features
            logEnabledFeatures();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start HTTP server", e);
            shutdown();
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.info("Shutting down HytaleAPI plugin...");

        // Shutdown event broadcaster
        if (eventBroadcaster != null) {
            eventBroadcaster.shutdown();
        }

        // Close WebSocket sessions gracefully
        if (channelInitializer != null) {
            var wsManager = channelInitializer.getWebSocketSessionManager();
            if (wsManager != null) {
                wsManager.getAllChannels().close();
            }
        }

        // Close server channel
        if (serverChannel != null) {
            serverChannel.close();
        }

        // Shutdown event loops
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        LOGGER.info("HytaleAPI plugin shutdown complete");
    }

    /**
     * Log enabled features for visibility.
     */
    private void logEnabledFeatures() {
        StringBuilder sb = new StringBuilder("Enabled features: ");

        sb.append("REST API");

        if (config.websocket().enabled()) {
            sb.append(", WebSocket (").append(config.websocket().path()).append(")");
        }

        if (config.tls().enabled()) {
            sb.append(", TLS");
        }

        sb.append(", Rate Limiting (").append(config.rateLimits().defaultRequestsPerMinute()).append("/min)");
        sb.append(", JWT Auth");

        LOGGER.info(sb.toString());

        // Log available endpoints
        LOGGER.info("Available endpoints:");
        LOGGER.info("  GET  /health              - Health check (no auth)");
        LOGGER.info("  POST /auth/token          - Get JWT token");
        LOGGER.info("  GET  /server/status       - Server status");
        LOGGER.info("  GET  /players             - Player list");
        LOGGER.info("  GET  /players/{uuid}      - Player details");
        LOGGER.info("  GET  /worlds              - World list");
        LOGGER.info("  GET  /worlds/{uuid}       - World details");
        LOGGER.info("  POST /admin/command       - Execute command");
        LOGGER.info("  POST /admin/kick          - Kick player");
        LOGGER.info("  POST /admin/ban           - Ban player");
        LOGGER.info("  POST /admin/broadcast     - Broadcast message");

        if (config.websocket().enabled()) {
            LOGGER.info("WebSocket events: player.join, player.leave, player.chat, server.status");
        }
    }

    /**
     * Get the plugin configuration.
     */
    public ApiConfig getConfig() {
        return config;
    }

    /**
     * Get the token generator for external access.
     */
    public TokenGenerator getTokenGenerator() {
        return tokenGenerator;
    }
}
