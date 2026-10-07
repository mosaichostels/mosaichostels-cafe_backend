package com.hostel.ordering.controller;

import com.hostel.ordering.security.LoginThrottle;
import com.hostel.ordering.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerThrottleTest {

    @Test
    void sixthWrongPasswordForTheSameUsernameIsRefusedWithoutTryingAgain() {
        AuthController controller = new AuthController();
        AuthService authService = mock(AuthService.class);
        when(authService.login(anyString(), anyString())).thenThrow(new IllegalArgumentException("bad"));
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "loginThrottle", new LoginThrottle());

        for (int i = 0; i < 5; i++) {
            assertEquals(401, controller.authenticateUser(Map.of("username", "admin", "password", "x")).getStatusCode().value());
        }
        ResponseEntity<?> sixth = controller.authenticateUser(Map.of("username", "admin", "password", "x"));

        assertEquals(429, sixth.getStatusCode().value());
        verify(authService, times(5)).login(anyString(), anyString());
    }
}
