package com.hostel.ordering.pms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hostel.ordering.pms.PmsWriteClaim.State;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

class MongoPmsWriteClaimStoreTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoPmsWriteClaimStore store = new MongoPmsWriteClaimStore(mongo);

    @Test
    void indexesEnforceOneOpenClaimPerTargetAndExpireDoneRows() throws Exception {
        assertEquals("pms_write_claims", PmsWriteClaim.class.getAnnotation(Document.class).value());
        Indexed open = PmsWriteClaim.class.getDeclaredField("openLockKey").getAnnotation(Indexed.class);
        assertTrue(open.unique());
        assertTrue(open.sparse());
        Indexed done = PmsWriteClaim.class.getDeclaredField("doneAt").getAnnotation(Indexed.class);
        assertEquals(86400, done.expireAfterSeconds());
    }

    @Test
    void claimIsASingleInsert() {
        PmsWriteClaim c = new PmsWriteClaim("k1", "f", "t", "h", "A", Instant.EPOCH);
        store.insert(c);
        verify(mongo).insert(c);
    }

    private String[] captureModify(Runnable call) {
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        call.run();
        verify(mongo).findAndModify(q.capture(), u.capture(), any(FindAndModifyOptions.class),
                eq(PmsWriteClaim.class));
        return new String[] {q.getValue().getQueryObject().toString(), u.getValue().getUpdateObject().toString()};
    }

    @Test
    void markDoneIsConditionalOnTheExpectedStateAndDropsTheLock() {
        String[] qu = captureModify(() -> store.markDone("k1", State.IN_PROGRESS, "{}", Instant.EPOCH));
        assertTrue(qu[0].contains("_id=k1") && qu[0].contains("state=IN_PROGRESS"), qu[0]);
        assertTrue(qu[1].contains("$unset") && qu[1].contains("openLockKey"), qu[1]);
        assertTrue(qu[1].contains("state=DONE") && qu[1].contains("doneAt"), qu[1]);
    }

    @Test
    void markUnconfirmedKeepsTheLockAndCanBeBoundedByAge() {
        String[] plain = captureModify(() -> store.markUnconfirmed("k1", null));
        assertTrue(plain[0].contains("state=IN_PROGRESS"), plain[0]);
        assertFalse(plain[0].contains("createdAt"), plain[0]);
        assertTrue(plain[1].contains("state=UNCONFIRMED"), plain[1]);
        assertFalse(plain[1].contains("openLockKey"), plain[1]);
    }

    @Test
    void staleMarkRequiresAnOldEnoughClaim() {
        String[] qu = captureModify(() -> store.markUnconfirmed("k1", Instant.EPOCH));
        assertTrue(qu[0].contains("createdAt") && qu[0].contains("$lte"), qu[0]);
    }

    @Test
    void deleteIsConditionalOnState() {
        PmsWriteClaim row = new PmsWriteClaim("k1", "f", "t", "h", "A", Instant.EPOCH);
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        when(mongo.findAndRemove(q.capture(), eq(PmsWriteClaim.class))).thenReturn(row);

        assertSame(row, store.delete("k1", State.UNCONFIRMED));
        String json = q.getValue().getQueryObject().toString();
        assertTrue(json.contains("_id=k1") && json.contains("state=UNCONFIRMED"), json);
    }
}
