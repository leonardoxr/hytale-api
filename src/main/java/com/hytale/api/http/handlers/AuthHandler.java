package com.hytale.api.http.handlers;

import com.google.gson.Gson;
import com.hytale.api.config.ApiConfig;
import com.hytale.api.config.ApiConfig.ClientConfig;
import com.hytale.api.dto.request.AuthRequest;
import com.hytale.api.exception.ApiException;
import com.hytale.api.security.TokenGenerator;
import io.netty.handler.codec.http.FullHttpRequest;
import org.bouncycastle.crypto.generators.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Handler for POST /auth/token endpoint.
 * Validates client credentials and returns JWT token.
 */
public final class AuthHandler {
    private static final Logger LOGGER = Logger.getLogger(AuthHandler.class.getName());
    private static final Gson GSON = new Gson();

    private final ApiConfig config;
    private final TokenGenerator tokenGenerator;

    public AuthHandler(ApiConfig config, TokenGenerator tokenGenerator) {
        this.config = config;
        this.tokenGenerator = tokenGenerator;
    }

    public String handle(FullHttpRequest request) {
        // Parse request body
        String body = request.content().toString(StandardCharsets.UTF_8);
        AuthRequest authRequest;

        try {
            authRequest = GSON.fromJson(body, AuthRequest.class);
        } catch (Exception e) {
            throw ApiException.BadRequest.invalidJson(e.getMessage());
        }

        if (authRequest == null || !authRequest.isValid()) {
            throw ApiException.BadRequest.missingField("clientId and secret required");
        }

        // Find client
        ClientConfig client = config.findClient(authRequest.clientId());
        if (client == null) {
            LOGGER.warning("Auth attempt with unknown client: " + authRequest.clientId());
            throw ApiException.Unauthorized.invalidCredentials();
        }

        // Verify password
        if (!verifyPassword(authRequest.secret(), client.secret())) {
            LOGGER.warning("Auth attempt with invalid secret for client: " + authRequest.clientId());
            throw ApiException.Unauthorized.invalidCredentials();
        }

        // Generate token
        LOGGER.info("Successful authentication for client: " + authRequest.clientId());
        var tokenResult = tokenGenerator.generateAccessToken(client);

        return tokenResult.toJson();
    }

    /**
     * Verify password against bcrypt hash.
     */
    private boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }

        try {
            // Handle both bcrypt hash and plain text (for development)
            if (storedHash.startsWith("$2")) {
                // BCrypt hash - use constant-time comparison
                return verifyBcrypt(password, storedHash);
            } else {
                // Plain text comparison (development only - not recommended)
                return password.equals(storedHash);
            }
        } catch (Exception e) {
            LOGGER.warning("Password verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verify password against bcrypt hash using Bouncy Castle.
     */
    private boolean verifyBcrypt(String password, String hash) {
        try {
            // Parse the bcrypt hash
            // Format: $2a$12$salt(22chars)hash(31chars)
            if (hash.length() < 60) {
                return false;
            }

            // Extract cost factor and salt from hash
            String[] parts = hash.split("\\$");
            if (parts.length != 4) {
                return false;
            }

            int cost = Integer.parseInt(parts[2]);
            String saltAndHash = parts[3];
            byte[] salt = decodeBcryptSalt(saltAndHash.substring(0, 22));

            // Generate hash of input password
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] generatedHash = BCrypt.generate(passwordBytes, salt, cost);

            // Encode and compare
            String generatedSaltHash = encodeBcryptSalt(salt) + encodeBcryptHash(generatedHash);
            return constantTimeEquals(saltAndHash, generatedSaltHash);

        } catch (Exception e) {
            return false;
        }
    }

    private static final String BCRYPT_CHARS =
            "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private byte[] decodeBcryptSalt(String encoded) {
        byte[] result = new byte[16];
        int idx = 0;
        for (int i = 0; i < 22 && idx < 16; i += 4) {
            int c1 = BCRYPT_CHARS.indexOf(encoded.charAt(i));
            int c2 = BCRYPT_CHARS.indexOf(encoded.charAt(i + 1));
            int c3 = i + 2 < 22 ? BCRYPT_CHARS.indexOf(encoded.charAt(i + 2)) : 0;
            int c4 = i + 3 < 22 ? BCRYPT_CHARS.indexOf(encoded.charAt(i + 3)) : 0;

            if (idx < 16) result[idx++] = (byte) ((c1 << 2) | (c2 >> 4));
            if (idx < 16) result[idx++] = (byte) ((c2 << 4) | (c3 >> 2));
            if (idx < 16) result[idx++] = (byte) ((c3 << 6) | c4);
        }
        return result;
    }

    private String encodeBcryptSalt(byte[] salt) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < salt.length; i += 3) {
            int b1 = salt[i] & 0xFF;
            int b2 = i + 1 < salt.length ? salt[i + 1] & 0xFF : 0;
            int b3 = i + 2 < salt.length ? salt[i + 2] & 0xFF : 0;

            sb.append(BCRYPT_CHARS.charAt(b1 >> 2));
            sb.append(BCRYPT_CHARS.charAt(((b1 & 0x03) << 4) | (b2 >> 4)));
            if (i + 1 < salt.length) {
                sb.append(BCRYPT_CHARS.charAt(((b2 & 0x0F) << 2) | (b3 >> 6)));
            }
            if (i + 2 < salt.length) {
                sb.append(BCRYPT_CHARS.charAt(b3 & 0x3F));
            }
        }
        return sb.toString();
    }

    private String encodeBcryptHash(byte[] hash) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hash.length && i < 23; i += 3) {
            int b1 = hash[i] & 0xFF;
            int b2 = i + 1 < hash.length ? hash[i + 1] & 0xFF : 0;
            int b3 = i + 2 < hash.length ? hash[i + 2] & 0xFF : 0;

            sb.append(BCRYPT_CHARS.charAt(b1 >> 2));
            sb.append(BCRYPT_CHARS.charAt(((b1 & 0x03) << 4) | (b2 >> 4)));
            if (i + 1 < hash.length) {
                sb.append(BCRYPT_CHARS.charAt(((b2 & 0x0F) << 2) | (b3 >> 6)));
            }
            if (i + 2 < hash.length) {
                sb.append(BCRYPT_CHARS.charAt(b3 & 0x3F));
            }
        }
        return sb.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
