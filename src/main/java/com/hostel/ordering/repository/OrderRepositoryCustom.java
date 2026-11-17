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
     * same room. An attempt that got no reply from eZee (marked UNCONFIRMED) is only claimable
     * when the admin acknowledged it. Returns null when the claim is refused.
     */
    Order claimForChargePost(String orderId, String room, boolean acknowledgeUnconfirmed);

    /** Orders whose claim has been held since before cutoffMs - the process that took it is gone. */
    List<Order> findStaleChargePosts(long cutoffMs);

}
