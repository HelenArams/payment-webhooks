package com.helenaramyan.webhooks.endpoint;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates endpoint signing secrets: 256 bits from {@link SecureRandom}, base64url-encoded and
 * prefixed with {@code whsec_} so they are recognisable in logs and secret scanners.
 */
@Component
public class SecretGenerator {

    public static final String PREFIX = "whsec_";
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
