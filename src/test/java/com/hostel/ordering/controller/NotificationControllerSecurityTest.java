package com.hostel.ordering.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationControllerSecurityTest {

    @Test
    void subscribeAndUnsubscribeRequireAStaffOrAdminRole() throws Exception {
        for (String name : new String[] {"subscribe", "unsubscribe"}) {
            PreAuthorize pre = NotificationController.class.getMethod(name, java.util.Map.class)
                    .getAnnotation(PreAuthorize.class);
            assertTrue(pre != null && pre.value().contains("STAFF"), name);
        }
    }
}
