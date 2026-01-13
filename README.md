# Hytale API Plugin

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

### Public Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/auth/token` | Obtain JWT token |

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

### WebSocket Events

| Event | Permission | Description |
|-------|------------|-------------|
| `player.connect` | `api.websocket.subscribe.players` | Player connecting |
| `player.join` | `api.websocket.subscribe.players` | Player fully joined |
| `player.leave` | `api.websocket.subscribe.players` | Player disconnected |
| `server.status` | `api.websocket.subscribe.status` | Periodic status update |

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
