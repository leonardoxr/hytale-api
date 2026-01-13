# Hytale API Plugin

![Java](https://img.shields.io/badge/Java-25-orange)
![Gradle](https://img.shields.io/badge/Gradle-9.2-blue)
![Default Port](https://img.shields.io/badge/default%20port-8080-green)
![License](https://img.shields.io/badge/license-MIT-brightgreen)

A secure REST and WebSocket API plugin for Hytale game servers. Provides authenticated endpoints for server management, player monitoring, and real-time event streaming.

## Features

- **REST API** - Full HTTP API for server management and monitoring
- **WebSocket Events** - Real-time player and server event streaming
- **JWT Authentication** - RS256 signed tokens with configurable expiry
- **Rate Limiting** - Token bucket algorithm with per-endpoint configuration
- **Permission System** - Hierarchical permissions with wildcard support
- **TLS Support** - Optional HTTPS with custom certificates
- **CORS** - Configurable cross-origin resource sharing

## Requirements

- Hytale Server (with plugin support)
- Java 25+
- Gradle 9.2+ (included via wrapper)

## Installation

1. Build the plugin:
   ```bash
   ./gradlew build
   ```

2. Copy `build/libs/hytale-api-1.0.0.jar` to your server's `plugins/` directory

3. Start the server - a default `config.json` will be generated

4. Configure clients and permissions in `plugins/hytale-api/config.json`

## Configuration

The plugin creates a `config.json` in its data directory on first run. See `config.example.json` for all options.

### Key Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `true` | Enable/disable the API |
| `port` | `8080` | HTTP server port |
| `bindAddress` | `0.0.0.0` | Network interface to bind |
| `tls.enabled` | `false` | Enable HTTPS |
| `websocket.enabled` | `true` | Enable WebSocket endpoint |

### Client Configuration

Clients are defined in the `clients` array with bcrypt-hashed secrets.

**Generating a bcrypt hash:**
```bash
# Using htpasswd (Apache)
htpasswd -bnBC 12 "" yourpassword | tr -d ':'

# Using Python
python -c "import bcrypt; print(bcrypt.hashpw(b'yourpassword', bcrypt.gensalt(12)).decode())"

# Using Node.js
node -e "const bcrypt=require('bcryptjs');console.log(bcrypt.hashSync('yourpassword',12))"
```

**Example configuration:**
```json
{
  "clients": [
    {
      "id": "my-client",
      "secret": "$2a$12$...",
      "permissions": ["api.players.read", "api.status.read"],
      "enabled": true
    }
  ]
}
```

## API Usage

### Authentication

```bash
# Get access token
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"client_id": "admin", "client_secret": "admin"}'

# Response
{"access_token": "eyJ...", "token_type": "Bearer", "expires_in": 3600}
```

### Protected Endpoints

```bash
# Get server status
curl http://localhost:8080/server/status \
  -H "Authorization: Bearer eyJ..."

# List players
curl http://localhost:8080/players \
  -H "Authorization: Bearer eyJ..."

# Execute command (requires api.admin.command permission)
curl -X POST http://localhost:8080/admin/command \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d '{"command": "say Hello World"}'
```

### WebSocket Connection

```javascript
const ws = new WebSocket('ws://localhost:8080/ws');

// Authenticate
ws.send(JSON.stringify({
  type: 'auth',
  token: 'eyJ...'
}));

// Subscribe to events
ws.send(JSON.stringify({
  type: 'subscribe',
  events: ['player.join', 'player.leave', 'server.status']
}));

// Receive events
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log(data.event, data.data);
};
```

## API Reference

### HTTP Status Codes

| Status | Meaning |
|--------|---------|
| `200` | Success |
| `400` | Bad request / Invalid JSON |
| `401` | Missing or invalid token |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `429` | Rate limited |
| `500` | Internal server error |

### Public Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/auth/token` | Obtain JWT token |

<details>
<summary><code>GET /health</code> - Response</summary>

```json
{
  "status": "healthy",
  "timestamp": 1705312200000
}
```
</details>

<details>
<summary><code>POST /auth/token</code> - Request & Response</summary>

**Request:**
```json
{
  "client_id": "admin",
  "client_secret": "yourpassword"
}
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```
</details>

### Protected Endpoints

| Method | Path | Permission | Description |
|--------|------|------------|-------------|
| GET | `/server/status` | `api.status.read` | Server status |
| GET | `/server/stats` | `api.status.read` | Detailed server statistics |
| GET | `/players` | `api.players.read` | List online players |
| GET | `/players/{uuid}` | `api.players.read` | Player details |
| GET | `/worlds` | `api.worlds.read` | List worlds |
| GET | `/worlds/{id}` | `api.worlds.read` | World details |
| GET | `/worlds/{id}/stats` | `api.worlds.read` | World statistics |
| POST | `/admin/command` | `api.admin.command` | Execute server command |
| POST | `/admin/kick` | `api.admin.kick` | Kick player |
| POST | `/admin/ban` | `api.admin.ban` | Ban player |
| POST | `/admin/broadcast` | `api.admin.broadcast` | Broadcast message |

<details>
<summary><code>GET /server/status</code> - Response</summary>

```json
{
  "name": "My Hytale Server",
  "version": "1.0.0",
  "players": 15,
  "maxPlayers": 100,
  "uptime": 3600000,
  "memory": {
    "used": 1073741824,
    "max": 4294967296
  }
}
```
</details>

<details>
<summary><code>GET /players</code> - Response</summary>

```json
{
  "count": 2,
  "players": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Steve",
      "world": "world_1",
      "position": { "x": 100.5, "y": 64.0, "z": -200.3 },
      "connectedTime": 1800000
    },
    {
      "uuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "name": "Alex",
      "world": "world_1",
      "position": { "x": 50.0, "y": 72.0, "z": 100.0 },
      "connectedTime": 900000
    }
  ]
}
```
</details>

<details>
<summary><code>GET /players/{uuid}</code> - Response</summary>

```json
{
  "uuid": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Steve",
  "world": "world_1",
  "position": { "x": 100.5, "y": 64.0, "z": -200.3 },
  "connectedTime": 1800000,
  "stats": {
    "health": 100,
    "deaths": 5,
    "kills": 12
  },
  "gameMode": "Adventure"
}
```
</details>

<details>
<summary><code>GET /worlds</code> - Response</summary>

```json
{
  "count": 2,
  "worlds": [
    {
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "name": "world_1",
      "players": 15,
      "type": "default"
    },
    {
      "uuid": "987fcdeb-51a2-3bc4-d567-890123456789",
      "name": "world_nether",
      "players": 3,
      "type": "nether"
    }
  ]
}
```
</details>

<details>
<summary><code>POST /admin/command</code> - Request & Response</summary>

**Request:**
```json
{
  "command": "say Hello everyone!"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Command queued for execution: say Hello everyone!"
}
```
</details>

<details>
<summary><code>POST /admin/kick</code> - Request & Response</summary>

**Request:**
```json
{
  "player": "Steve",
  "reason": "AFK too long"
}
```

**Response:**
```json
{
  "success": true,
  "action": "kick",
  "target": "Steve",
  "message": "Player kicked: AFK too long"
}
```
</details>

<details>
<summary><code>POST /admin/ban</code> - Request & Response</summary>

**Request:**
```json
{
  "player": "Griefer123",
  "reason": "Griefing",
  "duration": 1440,
  "permanent": false
}
```

**Response:**
```json
{
  "success": true,
  "action": "ban",
  "target": "Griefer123",
  "message": "Player banned for 1440 minutes"
}
```
</details>

<details>
<summary><code>POST /admin/broadcast</code> - Request & Response</summary>

**Request:**
```json
{
  "message": "Server restart in 5 minutes!"
}
```

**Response:**
```json
{
  "success": true,
  "action": "broadcast",
  "target": "all",
  "message": "Broadcast sent to 15 players"
}
```
</details>

<details>
<summary>Error Response Format</summary>

```json
{
  "error": "Forbidden",
  "code": "INSUFFICIENT_PERMISSIONS",
  "message": "Required permission: api.admin.command"
}
```
</details>

### WebSocket Events

| Event | Permission | Description |
|-------|------------|-------------|
| `player.connect` | `api.websocket.subscribe.players` | Player connecting |
| `player.join` | `api.websocket.subscribe.players` | Player fully joined |
| `player.leave` | `api.websocket.subscribe.players` | Player disconnected |
| `server.status` | `api.websocket.subscribe.status` | Periodic status update |

<details>
<summary>WebSocket Message Format</summary>

**Event message:**
```json
{
  "type": "player.join",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Steve",
    "world": "world_1"
  },
  "timestamp": 1705312200000
}
```

**Auth success:**
```json
{
  "type": "auth_success",
  "clientId": "admin",
  "expiresIn": 3600
}
```

**Error:**
```json
{
  "type": "error",
  "code": "INVALID_TOKEN",
  "message": "Token has expired",
  "timestamp": 1705312200000
}
```
</details>

## Permissions

Permissions use a hierarchical dot notation with wildcard support:

- `api.*` - All permissions
- `api.admin.*` - All admin actions
- `api.players.read` - Read player data
- `api.websocket.connect` - Connect to WebSocket
- `api.websocket.subscribe.*` - Subscribe to all events

## Security Considerations

- Change default client credentials before production use
- Use TLS in production environments
- Restrict `bindAddress` to localhost if using a reverse proxy
- Review and limit client permissions appropriately
- The RSA keypair is auto-generated on first run and stored in the plugin data directory

## License

MIT
