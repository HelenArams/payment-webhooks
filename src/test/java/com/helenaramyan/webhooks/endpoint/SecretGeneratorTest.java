package com.helenaramyan.webhooks.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecretGeneratorTest {

    private final SecretGenerator generator = new SecretGenerator();

    @Test
    void secretsArePrefixedAndUrlSafe() {
        String secret = generator.generate();

        assertThat(secret).startsWith("whsec_");
        // 32 bytes -> 43 base64url chars without padding
        assertThat(secret).hasSize("whsec_".length() + 43);
        assertThat(secret.substring("whsec_".length())).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void secretsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            assertThat(seen.add(generator.generate())).isTrue();
        }
    }
}
