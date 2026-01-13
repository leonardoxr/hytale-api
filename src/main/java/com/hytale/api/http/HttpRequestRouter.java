package com.hytale.api.http;

import com.hytale.api.config.ApiConfig;
import com.hytale.api.exception.ApiException;
import com.hytale.api.http.handlers.*;
import com.hytale.api.ratelimit.RateLimitMiddleware;
import com.hytale.api.security.ClientIdentity;
import com.hytale.api.security.TokenGenerator;
import com.hytale.api.security.TokenGenerator.ValidatedToken;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main HTTP request router using Java 21 pattern matching.
 * Routes requests to appropriate handlers based on method and path.
 */
@ChannelHandler.Sharable
public final class HttpRequestRouter extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = Logger.getLogger(HttpRequestRouter.class.getName());

    // Path patterns for UUID extraction
    private static final Pattern PLAYERS_DETAIL = Pattern.compile("^/players/([a-fA-F0-9-]+)$");
    private static final Pattern WORLDS_DETAIL = Pattern.compile("^/worlds/([^/]+)$");
    private static final Pattern WORLDS_STATS = Pattern.compile("^/worlds/([^/]+)/stats$");
    private static final Pattern ADMIN_PATTERN = Pattern.compile("^/admin/(command|kick|ban|broadcast)$");

    private final ApiConfig config;
    private final TokenGenerator tokenGenerator;

    // Handlers
    private final HealthHandler healthHandler;
    private final AuthHandler authHandler;
    private final StatusHandler statusHandler;
    private final PlayersHandler playersHandler;
    private final WorldsHandler worldsHandler;
    private final AdminHandler adminHandler;
    private final StatsHandler statsHandler;

    public HttpRequestRouter(ApiConfig config, TokenGenerator tokenGenerator) {
        this.config = config;
        this.tokenGenerator = tokenGenerator;

        // Initialize handlers
        this.healthHandler = new HealthHandler();
        this.authHandler = new AuthHandler(config, tokenGenerator);
        this.statusHandler = new StatusHandler();
        this.playersHandler = new PlayersHandler();
        this.worldsHandler = new WorldsHandler();
        this.adminHandler = new AdminHandler();
        this.statsHandler = new StatsHandler();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getPath(request.uri());
        HttpMethod method = request.method();

        LOGGER.fine(() -> "Request: %s %s".formatted(method, path));

        try {
            // Handle CORS preflight
            if (method == HttpMethod.OPTIONS) {
                handleCors(ctx, request);
                return;
            }

            // Route request
            String response = route(ctx, request, method, path);

            // Send successful response
            sendResponse(ctx, HttpResponseStatus.OK, response, request);

        } catch (ApiException e) {
            LOGGER.log(Level.FINE, "API error: " + e.getMessage(), e);
            sendErrorResponse(ctx, e, request);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unexpected error handling request", e);
            sendErrorResponse(ctx, new ApiException.InternalError("Internal server error"), request);
        }
    }

    /**
     * Route request to appropriate handler using pattern matching.
     */
    private String route(ChannelHandlerContext ctx, FullHttpRequest request, HttpMethod method, String path) {
        // Public endpoints (no auth required)
        if (path.equals("/health") && method == HttpMethod.GET) {
            return healthHandler.handle(request);
        }

        if (path.equals("/auth/token") && method == HttpMethod.POST) {
            return authHandler.handle(request);
        }

        // Protected endpoints - require authentication
        ClientIdentity identity = authenticate(request);

        // Server status
        if (path.equals("/server/status") && method == HttpMethod.GET) {
            return statusHandler.handle(request, identity);
        }

        // Server detailed stats
        if (path.equals("/server/stats") && method == HttpMethod.GET) {
            return statsHandler.handleServerStats(request, identity);
        }

        // Players
        if (path.equals("/players") && method == HttpMethod.GET) {
            return playersHandler.handleList(request, identity);
        }

        Matcher playerMatcher = PLAYERS_DETAIL.matcher(path);
        if (playerMatcher.matches() && method == HttpMethod.GET) {
            return playersHandler.handleDetail(request, identity, playerMatcher.group(1));
        }

        // Worlds
        if (path.equals("/worlds") && method == HttpMethod.GET) {
            return worldsHandler.handleList(request, identity);
        }

        // World stats - must come before worlds detail to avoid conflict
        Matcher worldStatsMatcher = WORLDS_STATS.matcher(path);
        if (worldStatsMatcher.matches() && method == HttpMethod.GET) {
            return statsHandler.handleWorldStats(request, identity, worldStatsMatcher.group(1));
        }

        Matcher worldMatcher = WORLDS_DETAIL.matcher(path);
        if (worldMatcher.matches() && method == HttpMethod.GET) {
            return worldsHandler.handleDetail(request, identity, worldMatcher.group(1));
        }

        // Admin endpoints
        Matcher adminMatcher = ADMIN_PATTERN.matcher(path);
        if (adminMatcher.matches() && method == HttpMethod.POST) {
            String action = adminMatcher.group(1).toUpperCase();
            return adminHandler.handle(request, identity, AdminHandler.AdminAction.valueOf(action));
        }

        // No match
        throw ApiException.NotFound.endpoint(path);
    }

    /**
     * Authenticate request and return client identity.
     */
    private ClientIdentity authenticate(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            throw ApiException.Unauthorized.missingToken();
        }

        // Parse Bearer token
        if (!authHeader.startsWith("Bearer ")) {
            throw ApiException.Unauthorized.invalidToken("Expected Bearer token");
        }

        String token = authHeader.substring(7);

        // Validate token
        ValidatedToken result = tokenGenerator.validateToken(token);

        return switch (result) {
            case ValidatedToken.Valid valid -> new ClientIdentity(
                    valid.clientId(),
                    valid.permissions(),
                    Instant.now(),
                    valid.expiry(),
                    valid.tokenId()
            );
            case ValidatedToken.Invalid invalid -> throw ApiException.Unauthorized.invalidToken(invalid.reason());
            case ValidatedToken.Expired expired -> throw ApiException.Unauthorized.expiredToken();
        };
    }

    /**
     * Handle CORS preflight request.
     */
    private void handleCors(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.NO_CONTENT
        );

        addCorsHeaders(response, request);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Send successful JSON response.
     */
    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                              String body, FullHttpRequest request) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        // Add rate limit headers
        Integer remaining = ctx.channel().attr(RateLimitMiddleware.REMAINING_TOKENS_KEY).get();
        if (remaining != null) {
            response.headers().set("X-RateLimit-Remaining", remaining);
        }

        addCorsHeaders(response, request);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Send error response.
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, ApiException error, FullHttpRequest request) {
        byte[] bytes = error.toJson().getBytes(StandardCharsets.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                error.status(),
                Unpooled.wrappedBuffer(bytes)
        );

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        // Add retry-after for rate limiting
        if (error instanceof ApiException.RateLimited rateLimited) {
            response.headers().set("Retry-After", rateLimited.retryAfterSeconds());
        }

        addCorsHeaders(response, request);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Add CORS headers to response.
     */
    private void addCorsHeaders(FullHttpResponse response, FullHttpRequest request) {
        var cors = config.cors();

        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && cors.isOriginAllowed(origin)) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        } else if (cors.allowedOrigins().contains("*")) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS,
                String.join(", ", cors.allowedMethods()));
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS,
                String.join(", ", cors.allowedHeaders()));

        if (!cors.exposedHeaders().isEmpty()) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS,
                    String.join(", ", cors.exposedHeaders()));
        }

        if (cors.allowCredentials()) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, cors.maxAge());
    }

    /**
     * Extract path from URI (remove query string).
     */
    private String getPath(String uri) {
        int queryStart = uri.indexOf('?');
        return queryStart > 0 ? uri.substring(0, queryStart) : uri;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.log(Level.WARNING, "Channel exception", cause);
        ctx.close();
    }
}
