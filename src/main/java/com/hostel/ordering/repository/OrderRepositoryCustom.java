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
}
