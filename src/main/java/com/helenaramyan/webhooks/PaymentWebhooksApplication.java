package com.helenaramyan.webhooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentWebhooksApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentWebhooksApplication.class, args);
    }
}
