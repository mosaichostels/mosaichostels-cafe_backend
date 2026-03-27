package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public OrderRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SearchResult searchOrders(SearchCriteria criteria, Pageable pageable) {
        Query query = buildQuery(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        if (pageable != null) {
            long total = mongoTemplate.count(query, Order.class);
            query.skip(pageable.getOffset());
            query.limit(pageable.getPageSize());
            List<Order> orders = mongoTemplate.find(query, Order.class);
            return new SearchResult(orders, total);
        } else {
            List<Order> orders = mongoTemplate.find(query, Order.class);
            return new SearchResult(orders, orders.size());
        }
    }

    private Query buildQuery(SearchCriteria criteria) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.status() != null && !criteria.status().isBlank()) {
            criteriaList.add(Criteria.where("status").is(criteria.status()));
        }

        if (criteria.dormitory() != null && !criteria.dormitory().isBlank()) {
            criteriaList.add(Criteria.where("dormitory").is(criteria.dormitory()));
        }

        if (criteria.dateFrom() != null && criteria.dateTo() != null) {
            criteriaList.add(Criteria.where("createdAt")
                    .gte(criteria.dateFrom())
                    .lte(criteria.dateTo()));
        }

        if (criteria.search() != null && !criteria.search().isBlank()) {
            String trimmed = criteria.search().trim();
            if (trimmed.matches("\\d+")) {
                criteriaList.add(Criteria.where("phoneNumber").regex(trimmed));
            } else {
                criteriaList.add(Criteria.where("bookingName").regex(trimmed, "i"));
            }
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        return query;
    }
}
