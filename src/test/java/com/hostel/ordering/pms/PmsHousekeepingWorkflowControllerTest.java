package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hostel.ordering.config.SecurityConfig;
import com.hostel.ordering.exception.GlobalExceptionHandler;
import com.hostel.ordering.model.CleaningAssignment;
import com.hostel.ordering.pms.dto.CleaningTaskList;
import java.util.List;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PmsHousekeepingWorkflowControllerTest.Cfg.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PmsHousekeepingWorkflowControllerTest {

    // not beans: Spring would try to autowire the mock's inherited @Autowired fields
    static final PmsHousekeepingWorkflowService SERVICE = mock(PmsHousekeepingWorkflowService.class);
    static final PmsHousekeepingWorkflowService.AssignRequest ASSIGN =
            new PmsHousekeepingWorkflowService.AssignRequest("sam", null);
    static final PmsHousekeepingWorkflowService.UpdateRequest UPDATE =
            new PmsHousekeepingWorkflowService.UpdateRequest("DONE", null, null, null);

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        static RoleHierarchy roleHierarchy() {
            return SecurityConfig.roleHierarchy();
        }

        @Bean
        PmsHousekeepingWorkflowController controller() {
            return new PmsHousekeepingWorkflowController(SERVICE);
        }
    }

    @Autowired
    PmsHousekeepingWorkflowController controller;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(SERVICE);
        when(SERVICE.tasks(any())).thenReturn(new CleaningTaskList(List.of(), List.of(), 1L, false));
        when(SERVICE.assign(any(), any(), any())).thenReturn(new CleaningAssignment());
        when(SERVICE.update(any(), any(), any())).thenReturn(new CleaningAssignment());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", role));
    }

    @Test
    void housekeepingAdminAndSuperAdminPass() {
        for (String role : new String[] {"ROLE_HOUSEKEEPING", "ROLE_ADMIN", "ROLE_SUPERADMIN"}) {
            as(role);
            assertEquals(false, controller.tasks(null).stale());
            controller.assign("2026-11-10|11", "k", ASSIGN);
            controller.update("f".repeat(24), "k", UPDATE);
        }
    }

    @Test
    void unauthenticatedAndOtherRolesAreRefused() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.tasks(null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.assign("k", "k", ASSIGN));
        as("ROLE_GUEST");
        assertThrows(AccessDeniedException.class, () -> controller.tasks(null));
        assertThrows(AccessDeniedException.class, () -> controller.assign("k", "k", ASSIGN));
        assertThrows(AccessDeniedException.class, () -> controller.update("k", "k", UPDATE));
        verifyNoInteractions(SERVICE);
    }

    @Test
    void httpStatusesFor400And409AndPassthrough() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PmsHousekeepingWorkflowController(SERVICE))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(SERVICE.tasks("bad")).thenThrow(new IllegalArgumentException("Dates must be in yyyy-MM-dd format"));
        mvc.perform(get("/pms/cleaning-tasks?date=bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/pms/cleaning-tasks")).andExpect(status().isOk());
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Idempotency-Key is required")).when(SERVICE).assign(any(), any(), any());
        mvc.perform(post("/pms/cleaning-tasks/2026-11-10%7C11/assign").contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"sam\"}")).andExpect(status().isBadRequest());
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "changed")).when(SERVICE).update(any(), any(), any());
        mvc.perform(put("/pms/cleaning-assignments/" + "a".repeat(24)).header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict());
        org.mockito.Mockito.doThrow(new PmsIdempotencyMismatchException("x")).when(SERVICE).update(any(), any(), any());
        mvc.perform(put("/pms/cleaning-assignments/" + "a".repeat(24)).header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isConflict());
    }
}
