package rw.gov.ptc.request.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** An internal supporting document of a request (never a bid document). */
@Entity
@Table(name = "request_document")
public class RequestDocument extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID requestId;
    public String fileName;
    public String contentType;
    public long sizeBytes;
    public String sha256;
    @JsonIgnore
    @Basic(fetch = FetchType.LAZY)
    public byte[] content;
    @Column(columnDefinition = "uuid")
    public UUID uploadedBy;
    public Instant uploadedAt = Instant.now();
}
