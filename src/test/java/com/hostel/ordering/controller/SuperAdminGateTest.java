package com.hostel.ordering.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuperAdminGateTest {

    private static String gate(String name) {
        for (Method m : UserController.class.getMethods()) {
            if (m.getName().equals(name)) return m.getAnnotation(PreAuthorize.class).value();
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void auditIsSuperAdminOnly() {
        assertEquals("hasRole('SUPERADMIN')", AuditController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void userManagementIsSuperAdminOnly() {
        for (String n : new String[] {"getAllUsers", "createUser", "deleteUser", "updateUser"}) {
            assertEquals("hasRole('SUPERADMIN')", gate(n), n);
        }
    }

    @Test
    void registerFcmStaysAuthenticated() {
        assertEquals("isAuthenticated()", gate("registerFcmTokenForCurrentUser"));
    }
}
