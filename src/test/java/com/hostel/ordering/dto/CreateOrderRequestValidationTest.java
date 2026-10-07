package com.hostel.ordering.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CreateOrderRequestValidationTest {

    @Test
    void overlongBookingNameIsRejected() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setBookingName("x".repeat(101));
        req.setDormitory("Dorm");
        req.setItems(new ArrayList<>(List.of(new com.hostel.ordering.model.OrderItem())));

        var violations = Validation.buildDefaultValidatorFactory().getValidator().validate(req);

        assertFalse(violations.isEmpty());
    }
}
