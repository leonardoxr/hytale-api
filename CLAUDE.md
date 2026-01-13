# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin JAR
./gradlew build

# Clean and rebuild
./gradlew clean build

# Compile only (faster, no JAR packaging)
./gradlew compileJava
```

The output JAR is placed in `build/libs/hytale-api-1.0.0.jar`.

## Project Overview

Hytale API Plugin is a Netty-based REST and WebSocket server that runs as a plugin inside a Hytale game server. It provides authenticated API access for server management and real-time event streaming.

**Java Version:** 25 (uses pattern matching, records, sealed interfaces, virtual threads)

**Dependencies:** All runtime dependencies (Netty, Nimbus JOSE JWT, Gson) are bundled in HytaleServer.jar and accessed via `compileOnly`.

## Architecture

### Entry Point
`ApiPlugin` extends `JavaPlugin` from the Hytale server SDK. Lifecycle: `setup()` → `start()` → `shutdown()`.

### HTTP Layer (Netty Pipeline)
```
SocketChannel
  └─ SslHandler (optional TLS)
  └─ IdleStateHandler
  └─ HttpServerCodec
  └─ ChunkedWriteHandler
  └─ HttpObjectAggregator
  └─ RateLimitMiddleware
  └─ WebSocketServerProtocolHandler (handles /ws upgrade)
  └─ WebSocketHandler
  └─ HttpRequestRouter
```

`ApiChannelInitializer` assembles this pipeline. All handlers marked `@Sharable` are singleton instances.

### Request Routing
`HttpRequestRouter` uses regex patterns and Java 21 pattern matching to dispatch requests:
- Public endpoints: `/health`, `/auth/token`
- Protected endpoints require Bearer JWT in Authorization header
- Path parameters extracted via regex groups (e.g., `/players/{uuid}`)

### Authentication Flow
1. Client POSTs credentials to `/auth/token`
2. `AuthHandler` validates via bcrypt, returns signed JWT (RS256)
3. Subsequent requests include `Authorization: Bearer <token>`
4. `TokenGenerator` validates signature, expiry, issuer
5. `ClientIdentity` record carries permissions through request lifecycle

### Permission System
Hierarchical wildcards defined in `ApiPermissions`:
- `api.*` grants all
- `api.admin.*` grants all admin actions
- Specific: `api.players.read`, `api.admin.kick`, etc.

Checked via `ClientIdentity.hasPermission()` and `ApiPermissions.matches()`.

### WebSocket Events
`EventBroadcaster` subscribes to Hytale server events and pushes to WebSocket clients:
- `player.join`, `player.leave`, `player.connect`
- `server.status` (periodic broadcast via virtual thread scheduler)

Clients authenticate via `{"type":"auth","token":"..."}` message, then subscribe with `{"type":"subscribe","events":["player.*"]}`.

### Configuration
`ApiConfig` is a record hierarchy loaded from `config.json` in the plugin data directory. Nested records: `TlsConfig`, `JwtConfig`, `ClientConfig`, `RateLimitConfig`, `CorsConfig`, `WebSocketConfig`, `AuditConfig`.

### Error Handling
`ApiException` is a sealed class with typed subclasses: `BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `InternalError`, `RateLimited`. Each produces consistent JSON error responses.

## Key Patterns

- **Records for DTOs:** All request/response types in `dto/` are records
- **Sealed interfaces:** `ValidatedToken` uses sealed interface with `Valid`, `Invalid`, `Expired` cases
- **Pattern matching:** Switch expressions in router and handlers destructure sealed types
- **Sharable handlers:** Netty handlers marked `@Sharable` are thread-safe singletons

## API Endpoints

| Method | Path | Auth | Permission |
|--------|------|------|------------|
| GET | /health | No | - |
| POST | /auth/token | No | - |
| GET | /server/status | Yes | api.status.read |
| GET | /server/stats | Yes | api.status.read |
| GET | /players | Yes | api.players.read |
| GET | /players/{uuid} | Yes | api.players.read |
| GET | /worlds | Yes | api.worlds.read |
| GET | /worlds/{id} | Yes | api.worlds.read |
| GET | /worlds/{id}/stats | Yes | api.worlds.read |
| POST | /admin/command | Yes | api.admin.command |
| POST | /admin/kick | Yes | api.admin.kick |
| POST | /admin/ban | Yes | api.admin.ban |
| POST | /admin/broadcast | Yes | api.admin.broadcast |
