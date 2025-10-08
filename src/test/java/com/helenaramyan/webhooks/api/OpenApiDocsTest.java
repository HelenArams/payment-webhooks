package com.helenaramyan.webhooks.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void publishesAllV1Paths() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("payment-webhooks"))
                .andExpect(jsonPath("$.paths['/v1/endpoints'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/events'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/deliveries'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/deliveries/{id}/replay'].post").exists());
    }
}
