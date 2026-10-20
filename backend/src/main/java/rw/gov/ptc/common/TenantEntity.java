package rw.gov.ptc.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.UUID;

/** Entity owned by an institution; institution_id is stamped from the request context. */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @JsonIgnore
    @Column(name = "institution_id", columnDefinition = "uuid", nullable = false, updatable = false)
    public UUID institutionId;

    @PrePersist
    void stampTenant() {
        if (institutionId == null) {
            institutionId = TenantContext.requireInstitution();
        }
    }
}
