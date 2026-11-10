package com.hostel.ordering.pms;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import com.hostel.ordering.pms.PmsWriteClaim.State;
import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Claims in Mongo: insert to claim, findAndModify / findAndRemove on {_id, state} for every transition. */
@Component
public class MongoPmsWriteClaimStore implements PmsWriteClaimStore {

    private final MongoTemplate mongo;

    public MongoPmsWriteClaimStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(PmsWriteClaim claim) {
        mongo.insert(claim);
    }

    @Override
    public PmsWriteClaim find(String id) {
        return mongo.findById(id, PmsWriteClaim.class);
    }

    @Override
    public PmsWriteClaim findOpen(String lockKey) {
        return mongo.findOne(new Query(where("openLockKey").is(lockKey)), PmsWriteClaim.class);
    }

    @Override
    public List<PmsWriteClaim> findByState(State state) {
        return mongo.find(new Query(where("state").is(state.name())), PmsWriteClaim.class);
    }

    @Override
    public PmsWriteClaim markDone(String id, State from, String resultJson, Instant doneAt) {
        Update update = new Update().set("state", State.DONE.name()).set("doneAt", doneAt).unset("openLockKey");
        if (resultJson != null) {
            update.set("resultJson", resultJson);
        }
        return modify(inState(id, from), update);
    }

    @Override
    public PmsWriteClaim markUnconfirmed(String id, Instant createdBefore) {
        Criteria c = inState(id, State.IN_PROGRESS);
        if (createdBefore != null) {
            c = c.and("createdAt").lte(createdBefore);
        }
        return modify(c, new Update().set("state", State.UNCONFIRMED.name()));
    }

    @Override
    public PmsWriteClaim delete(String id, State state) {
        return mongo.findAndRemove(new Query(inState(id, state)), PmsWriteClaim.class);
    }

    private static Criteria inState(String id, State state) {
        return where("_id").is(id).and("state").is(state.name());
    }

    private PmsWriteClaim modify(Criteria criteria, Update update) {
        return mongo.findAndModify(new Query(criteria), update, FindAndModifyOptions.options().returnNew(false),
                PmsWriteClaim.class);
    }
}
