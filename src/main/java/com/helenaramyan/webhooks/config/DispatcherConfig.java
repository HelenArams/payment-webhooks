package com.helenaramyan.webhooks.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
public class DispatcherConfig {

    /** Polling is only switched on when enabled, so tests can drive the dispatcher by hand. */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "webhooks.dispatcher", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingEnabled {
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService webhookSendExecutor(WebhookProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "webhook-send-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(properties.dispatcher().concurrency(), factory);
    }

    /**
     * Dedicated client for receivers: tight connect/read timeouts and, with the JDK client, no
     * redirect following, so a 3xx counts as a failed attempt instead of a request to an
     * unexpected host.
     */
    @Bean
    public RestClient webhookRestClient(RestClient.Builder builder, WebhookProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.dispatcher().connectTimeout())
                .withReadTimeout(properties.dispatcher().timeout());
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
