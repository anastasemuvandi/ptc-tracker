package rw.gov.ptc.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Every entity has an application-assigned UUIDv7 key. Because the id is set
 * before persist, {@link Persistable#isNew()} is tracked explicitly so Spring
 * Data issues an INSERT rather than a SELECT-then-merge.
 *
 * Entities expose public fields and hold foreign keys as plain UUID columns;
 * there are no lazy associations, so no proxies are involved.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    public UUID id = Ids.newId();

    @Transient
    private boolean fresh = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        fresh = false;
    }
}
