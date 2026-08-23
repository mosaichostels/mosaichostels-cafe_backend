package com.hostel.ordering.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    @Test
    void chargePostFields_defaultToNull_andAreSettable() {
        Order order = new Order();
        assertNull(order.getChargePostStatus());
        assertNull(order.getChargePostRequestId());
        assertNull(order.getChargePostError());
        assertNull(order.getChargePostRoom());
        assertNull(order.getChargePostFolio());
        assertNull(order.getChargePostAt());

        order.setChargePostStatus("QUEUED");
        order.setChargePostRequestId("2805");
        order.setChargePostError(null);
        order.setChargePostRoom("101");
        order.setChargePostFolio("8");
        order.setChargePostAt(1700000000000L);

        assertEquals("QUEUED", order.getChargePostStatus());
        assertEquals("2805", order.getChargePostRequestId());
        assertEquals("101", order.getChargePostRoom());
        assertEquals("8", order.getChargePostFolio());
        assertEquals(1700000000000L, order.getChargePostAt());
    }
}
