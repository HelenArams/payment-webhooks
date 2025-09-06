package com.helenaramyan.webhooks.signature;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    // Known vector: HMAC-SHA256(secret, "1726000000." + BODY) computed independently with Python's hmac.
    private static final String SECRET = "whsec_c2VjcmV0LWZvci10ZXN0aW5nLW9ubHk";
    private static final long TIMESTAMP = 1_726_000_000L;
    private static final String BODY =
            "{\"id\":\"a0f1e2d3-1111-2222-3333-444455556666\",\"type\":\"payment.captured\","
                    + "\"data\":{\"paymentId\":\"pay_42\",\"amount\":1999,\"currency\":\"EUR\"}}";
    private static final String EXPECTED =
            "v1=bfc4c0edadd967d7a27b50e034d15266d3b80bc96037a5144fc6b218a0c3ed0a";

    private final WebhookSigner signer = new WebhookSigner();
    private final Instant now = Instant.ofEpochSecond(TIMESTAMP + 30);
    private final Duration tolerance = Duration.ofMinutes(5);

    @Test
    void producesKnownVector() {
        assertThat(signer.sign(SECRET, TIMESTAMP, BODY)).isEqualTo(EXPECTED);
    }

    @Test
    void timestampIsPartOfTheSignedPayload() {
        String shiftedByOneSecond = signer.sign(SECRET, TIMESTAMP + 1, BODY);

        assertThat(shiftedByOneSecond)
                .isNotEqualTo(EXPECTED)
                .isEqualTo("v1=6b16643def6d9e2ee848f3ff66bee92bed5c7b7a08df91eb6983cac57b4fb3bc");
    }

    @Test
    void verifiesGenuineSignatureCaseInsensitively() {
        assertThat(signer.verify(SECRET, TIMESTAMP, BODY, EXPECTED, now, tolerance)).isTrue();
        assertThat(signer.verify(SECRET, TIMESTAMP, BODY, EXPECTED.toUpperCase().replace("V1", "v1"), now, tolerance))
                .isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongSecretAndUnknownScheme() {
        assertThat(signer.verify(SECRET, TIMESTAMP, BODY.replace("1999", "1"), EXPECTED, now, tolerance)).isFalse();
        assertThat(signer.verify("whsec_other", TIMESTAMP, BODY, EXPECTED, now, tolerance)).isFalse();
        assertThat(signer.verify(SECRET, TIMESTAMP, BODY, EXPECTED.replace("v1=", "v2="), now, tolerance)).isFalse();
        assertThat(signer.verify(SECRET, TIMESTAMP, BODY, null, now, tolerance)).isFalse();
    }

    @Test
    void rejectsStaleTimestampEvenWithValidSignature() {
        Instant sixMinutesLater = Instant.ofEpochSecond(TIMESTAMP).plus(Duration.ofMinutes(6));

        assertThat(signer.verify(SECRET, TIMESTAMP, BODY, EXPECTED, sixMinutesLater, tolerance)).isFalse();
    }
}
