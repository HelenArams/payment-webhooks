package com.helenaramyan.webhooks.signature;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs webhook payloads with HMAC-SHA256 over {@code "<unix-timestamp>.<body>"}.
 *
 * <p>Binding the timestamp into the signed string means a captured request cannot be replayed
 * later with a fresh timestamp: the receiver checks the timestamp is recent and that the signature
 * matches the <em>same</em> timestamp. The header value is versioned ({@code v1=...}) so the scheme
 * can be rotated without breaking existing receivers.
 */
@Component
public class WebhookSigner {

    public static final String SCHEME = "v1";
    public static final String HEADER_ID = "Webhook-Id";
    public static final String HEADER_TIMESTAMP = "Webhook-Timestamp";
    public static final String HEADER_SIGNATURE = "Webhook-Signature";

    private static final String ALGORITHM = "HmacSHA256";

    /** Returns the full header value, e.g. {@code v1=5f1a...}. */
    public String sign(String secret, long timestampSeconds, String body) {
        return SCHEME + "=" + hexDigest(secret, timestampSeconds, body);
    }

    /**
     * Receiver-side verification, as a reference for integrators and for our own tests.
     *
     * @param tolerance maximum accepted skew between {@code now} and the timestamp header
     */
    public boolean verify(
            String secret, long timestampSeconds, String body, String signatureHeader, Instant now, Duration tolerance) {
        if (signatureHeader == null || !signatureHeader.startsWith(SCHEME + "=")) {
            return false;
        }
        long skew = Math.abs(now.getEpochSecond() - timestampSeconds);
        if (skew > tolerance.getSeconds()) {
            return false;
        }
        byte[] expected = hexDigest(secret, timestampSeconds, body).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signatureHeader.substring(SCHEME.length() + 1).toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    static String hexDigest(String secret, long timestampSeconds, String body) {
        String signedPayload = timestampSeconds + "." + body;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
