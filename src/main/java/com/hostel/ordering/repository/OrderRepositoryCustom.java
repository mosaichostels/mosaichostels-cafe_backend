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
     * Returns the order if claim succeeded (chargePostStatus was null/not set before).
     * Returns null if another thread already claimed it.
     */
    Order claimForChargePost(String orderId);

}
