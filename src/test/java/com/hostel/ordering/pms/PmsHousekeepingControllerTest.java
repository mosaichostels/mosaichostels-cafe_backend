package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.exception.GlobalExceptionHandler;
import com.hostel.ordering.pms.dto.RoomStatusWriteRequest;
import com.hostel.ordering.pms.dto.RoomStatusWriteResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsHousekeepingControllerTest.Cfg.class)
class PmsHousekeepingControllerTest {

    // not beans: Spring would try to autowire the mock's inherited @Autowired fields
    static final PmsRoomStatusWriteService WRITES = mock(PmsRoomStatusWriteService.class);
    static final RoomStatusWriteRequest BODY = new RoomStatusWriteRequest("21", "CLEAN", null);
    static final PmsWriteResult<RoomStatusWriteResult> RESULT =
            new PmsWriteResult<>(new RoomStatusWriteResult("11", "21", "CLEAN", "Clean", true), true);

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsHousekeepingController controller() {
            return new PmsHousekeepingController(mock(PmsHousekeepingService.class), WRITES);
        }
    }

    @Autowired
    PmsHousekeepingController controller;

    @BeforeEach
    void setUp() {
        reset(WRITES);
        when(WRITES.setStatus(any(), any(), any())).thenReturn(RESULT);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void staffAdminAndSuperAdminPass() {
        for (String role : new String[] {"ROLE_HOUSEKEEPING", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            as(role);
            assertEquals(RESULT, controller.setStatus("11", "k", BODY));
        }
    }

    @Test
    void unauthenticatedAndOtherRolesAreRefused() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.setStatus("11", "k", BODY));
        as("ROLE_GUEST");
        assertThrows(AccessDeniedException.class, () -> controller.setStatus("11", "k", BODY));
        verifyNoInteractions(WRITES);
    }

    @Test
    void httpStatuses() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PmsHousekeepingController(null, WRITES))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String json = "{\"unitId\":\"21\",\"status\":\"CLEAN\"}";
        mvc.perform(post("/pms/housekeeping/rooms/11/status").header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.value.hkStatus").value("Clean"));
        doThrow(new IllegalArgumentException("Idempotency-Key is required")).when(WRITES).setStatus(any(), any(), any());
        mvc.perform(post("/pms/housekeeping/rooms/11/status").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        doThrow(new PmsFeatureDisabledException("x")).when(WRITES).setStatus(any(), any(), any());
        mvc.perform(post("/pms/housekeeping/rooms/11/status").header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        doThrow(new PmsUnconfirmedException("x")).when(WRITES).setStatus(any(), any(), any());
        mvc.perform(post("/pms/housekeeping/rooms/11/status").header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isConflict());
    }
}
