package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderRepositoryCustom {

    SearchResult searchOrders(SearchCriteria criteria, Pageable pageable);

    record SearchCriteria(
            String status,
            String dormitory,
            String search,
            Long dateFrom,
            Long dateTo,
            boolean paginated
    ) {}

    record SearchResult(List<Order> orders, long totalElements) {}
}
