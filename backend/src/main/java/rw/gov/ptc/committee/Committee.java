package rw.gov.ptc.committee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A public tender committee within an institution. */
@Entity
@Table(name = "committee")
public class Committee extends TenantEntity {

    public String name;
    public String code;
    public int size;
    public String regulatorAuthRef;
    public LocalDate regulatorAuthDate;
    public String status = "ACTIVE";
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
