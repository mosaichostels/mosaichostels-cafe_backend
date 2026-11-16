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
import java.util.Arrays;
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

    static Criteria claimCriteria(String orderId, String room, boolean acknowledgeUnconfirmed) {
        // A FAILED attempt may be retried, but if it already put part of the charge on a folio the
        // retry must go to that same room - eZee cannot void, so a second room means a double charge.
        List<Criteria> failedRetry = new ArrayList<>();
        failedRetry.add(Criteria.where("chargePostStatus").is("FAILED"));
        failedRetry.add(new Criteria().orOperator(
                Criteria.where("chargePostedItems").in(Arrays.asList(null, new ArrayList<>())),
                Criteria.where("chargePostRoom").is(room)));
        if (!acknowledgeUnconfirmed) {
            // No reply from eZee: the item may already be on the folio, so a human has to look first.
            failedRetry.add(Criteria.where("chargePostedItems").not().regex("^UNCONFIRMED:"));
        }
        return Criteria.where("_id").is(orderId)
                .and("status").is("DELIVERED")
                .orOperator(Criteria.where("chargePostStatus").is(null),
                        new Criteria().andOperator(failedRetry.toArray(new Criteria[0])));
    }

    // chargePostAt is stamped at claim time so a claim whose process died can be found later.
    // The room is stored too: a claim interrupted before its final save would otherwise leave no
    // room on record, and the same-room rule could never be satisfied again.
    static Update claimUpdate(String room) {
        return new Update().set("chargePostStatus", "IN_PROGRESS")
                .set("chargePostAt", System.currentTimeMillis())
                .set("chargePostRoom", room);
    }

    @Override
    public List<Order> findStaleChargePosts(long cutoffMs) {
        return mongoTemplate.find(new Query(Criteria.where("chargePostStatus").is("IN_PROGRESS")
                .and("chargePostAt").lt(cutoffMs)), Order.class);
    }

    @Override
    public Order claimForChargePost(String orderId, String room, boolean acknowledgeUnconfirmed) {
        Update update = claimUpdate(room);
        return mongoTemplate.findAndModify(
                new Query(claimCriteria(orderId, room, acknowledgeUnconfirmed)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                Order.class
        );
    }
}
