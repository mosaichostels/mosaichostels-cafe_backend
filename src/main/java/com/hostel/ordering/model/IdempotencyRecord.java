package com.hostel.ordering.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * A stored result for one Idempotency-Key.
 *
 * <p>These used to live in a process-local map, which meant a restart erased them. The backend
 * runs on a Hugging Face Space that sleeps and redeploys, so a client retrying across a restart
 * would be treated as a first attempt and could place a second order or post a second charge.
 *
 * <p>{@code createdAt} carries a TTL index, so Mongo expires records itself and the collection
 * does not grow without bound.
 */
@Document(collection = "idempotency_keys")
public class IdempotencyRecord {

    /** The Idempotency-Key header value. */
    @Id
    private String id;

    /** The cached response body. Stored with a type discriminator so it deserializes back. */
    private Object result;

    @Indexed(expireAfterSeconds = 3600)
    private Date createdAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String id, Object result) {
        this.id = id;
        this.result = result;
        this.createdAt = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
