package rw.gov.ptc.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Answer to a configured checklist prompt (types B, E, F, G). */
@Entity
@Table(name = "checklist_response")
public class ChecklistResponse extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String itemCode;
    public String answer;
    public String note;
    @Column(columnDefinition = "uuid")
    public UUID answeredBy;
    public Instant answeredAt = Instant.now();
    @Version
    public Long version;
}
