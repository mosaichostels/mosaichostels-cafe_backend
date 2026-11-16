package com.hostel.ordering.pms;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetiredPmsRoutesTest {

    @Test
    void reconcileAndPublicRoomWriteAreAbsent() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.hostel.ordering.pms.PmsReconcileController"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.hostel.ordering.pms.PmsReconcileService"));
        assertFalse(Arrays.stream(PmsHousekeepingController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("setStatus")));
    }
}
