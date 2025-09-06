package com.helenaramyan.webhooks.endpoint;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EndpointRepository endpoints;

    @BeforeEach
    void clean() {
        endpoints.deleteAll();
    }

    @Test
    void registerReturnsSecretOnceAndListNeverExposesIt() throws Exception {
        mvc.perform(post("/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://merchant.example/hooks","eventTypes":["payment.captured","payment.refunded"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.url").value("https://merchant.example/hooks"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.eventTypes", containsInAnyOrder("payment.captured", "payment.refunded")))
                .andExpect(jsonPath("$.secret", startsWith("whsec_")));

        mvc.perform(get("/v1/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].url").value("https://merchant.example/hooks"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    @Test
    void rejectsNonHttpUrlAndMalformedEventTypesAsProblemDetails() throws Exception {
        mvc.perform(post("/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"ftp://merchant.example/hooks","eventTypes":["Payment Captured"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void unknownEndpointIsNotFoundProblem() throws Exception {
        mvc.perform(get("/v1/endpoints/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.detail").value("endpoint 00000000-0000-0000-0000-000000000000 not found"));
    }
}
