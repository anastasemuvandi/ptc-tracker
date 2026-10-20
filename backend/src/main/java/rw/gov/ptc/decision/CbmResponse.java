package rw.gov.ptc.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** The Chief Budget Manager's response to an issued recommendation. Separate act; never edits the decision. */
@Entity
@Table(name = "cbm_response")
public class CbmResponse extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID decisionId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String response;
    public String reasons;
    @Column(columnDefinition = "uuid")
    public UUID respondedBy;
    public Instant respondedAt = Instant.now();
}
