package com.hostel.ordering.repository;

import com.hostel.ordering.model.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.List;
@Repository
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public OrderRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Order> searchOrders(SearchCriteria criteria) {
        Query query = buildQuery(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, Order.class);
    }

    private Query buildQuery(SearchCriteria criteria) {
        List<Criteria> criteriaList = new ArrayList<>();

        if (criteria.status() != null && !criteria.status().isBlank()) {
            criteriaList.add(Criteria.where("status").is(criteria.status()));
        }

        if (criteria.dormitory() != null && !criteria.dormitory().isBlank()) {
            criteriaList.add(Criteria.where("dormitory").is(criteria.dormitory()));
        }

        if (criteria.date() != null) {
            long startOfDay = criteria.date();
            long endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1;
            criteriaList.add(Criteria.where("createdAt").gte(startOfDay).lte(endOfDay));
        } else if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (criteria.dateFrom() != null)
                dateCriteria = dateCriteria.gte(criteria.dateFrom());
            if (criteria.dateTo() != null)
                dateCriteria = dateCriteria.lte(criteria.dateTo());
            criteriaList.add(dateCriteria);
        }

        if (criteria.search() != null && !criteria.search().isBlank()) {
            criteriaList.add(Criteria.where("bookingName").regex(Pattern.quote(criteria.search().trim()), "i"));
        }

        Query query = new Query();
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }
        return query;
    }

    @Override
    public Order claimForChargePost(String orderId) {
        Criteria criteria = Criteria.where("_id").is(orderId).and("chargePostStatus").is(null);
        Update update = new Update().set("chargePostStatus", "IN_PROGRESS");
        return mongoTemplate.findAndModify(
                new Query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Order.class
        );
    }
}
