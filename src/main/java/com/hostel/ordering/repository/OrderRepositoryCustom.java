package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import java.util.List;
public interface OrderRepositoryCustom {

    List<Order> searchOrders(SearchCriteria criteria);

    record SearchCriteria(
            String status,
            String dormitory,
            String search,
            Long dateFrom,
            Long dateTo,
            Long date) {
    }
    /**
     * Atomically claim an order for chargepost by setting chargePostStatus to "IN_PROGRESS".
     * Succeeds only for a DELIVERED order that was never posted, or one whose earlier attempt
     * FAILED and which either charged nothing yet or already holds part of the charge in this
     * same room. Returns null when the claim is refused.
     */
    Order claimForChargePost(String orderId, String room);

}
