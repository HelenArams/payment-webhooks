package com.helenaramyan.webhooks.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentWebhooksApi() {
        return new OpenAPI().info(new Info()
                .title("payment-webhooks")
                .version("v1")
                .description("""
                        Register receiver endpoints, emit payment events, inspect and replay deliveries.

                        Every webhook is a POST with `Webhook-Id`, `Webhook-Timestamp` and
                        `Webhook-Signature: v1=<hex hmac-sha256(secret, timestamp + "." + body)>`.
                        """)
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
