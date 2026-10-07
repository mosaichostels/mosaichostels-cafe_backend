package com.hostel.ordering.controller;

import com.hostel.ordering.config.RateLimitFilter;
import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.config.WebConfig;
import com.hostel.ordering.security.JwtUtils;
import com.hostel.ordering.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, WebConfig.class, RateLimitFilter.class})
@TestPropertySource(properties = "cors.allowed-origins=https://app.example")
class HealthAndCorsTest {

    @Autowired MockMvc mvc;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    // The application class declares a bootstrap CommandLineRunner that needs it.
    @MockBean com.hostel.ordering.service.AuthService authService;

    @Test
    void preflightFromTheWebAppIsAllowedAndMayCarryIdempotencyKey() throws Exception {
        mvc.perform(options("/orders")
                        .header("Origin", "https://app.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));
    }

    @Test
    void healthIsPublicAndDoesNotRevealAccountNames() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.adminExists").doesNotExist());
    }
}
