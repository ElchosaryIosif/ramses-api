package gr.ramsesbazaar.core.security;

import gr.ramsesbazaar.core.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Stateless admin sessions: {@code base64url("exp.pwv") + "." + HMAC}. There is one admin and
 * the shop front calls the API cross-origin, so a signed bearer token avoids third-party cookies
 * and CSRF entirely. {@code pwv} is the password version; changing the password bumps it and
 * every older token stops verifying.
 */
@Slf4j
@Service
public class TokenService {

    public static final Duration SHORT_SESSION = Duration.ofHours(12);
    public static final Duration LONG_SESSION = Duration.ofDays(30);

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] key;

    public TokenService(AppProperties props) {
        if (props.hasSessionSecret()) {
            key = props.sessionSecret().getBytes(StandardCharsets.UTF_8);
        } else {
            log.warn("SESSION_SECRET is not set: using a random key, so admins are logged out on every restart");
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        }
    }

    public record Issued(String token, long exp) {
    }

    public Issued issue(int passwordVersion, boolean remember) {
        long exp = System.currentTimeMillis() + (remember ? LONG_SESSION : SHORT_SESSION).toMillis();
        String payload = ENC.encodeToString((exp + "." + passwordVersion).getBytes(StandardCharsets.UTF_8));
        return new Issued(payload + "." + sign(payload), exp);
    }

    public boolean verify(String token, int passwordVersion) {
        if (token == null) return false;
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return false;
        String payload = token.substring(0, dot);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(given, sign(payload).getBytes(StandardCharsets.US_ASCII))) return false;
        try {
            String[] parts = new String(DEC.decode(payload), StandardCharsets.UTF_8).split("\\.");
            return parts.length == 2
                    && Long.parseLong(parts[0]) > System.currentTimeMillis()
                    && Integer.parseInt(parts[1]) == passwordVersion;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
