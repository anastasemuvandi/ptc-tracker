package rw.gov.ptc.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A committee file (agenda-able matter) of item type A..I. file_number is the human-facing reference. */
@Entity
@Table(name = "proc_file")
public class ProcFile extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public String fileNumber;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(1)")
    public String itemType;
    public String tenderRef;
    @Column(columnDefinition = "uuid")
    public UUID contractId;
    public String title;
    public String procurementType;
    public String procurementMethod;
    @Column(columnDefinition = "uuid")
    public UUID procurementOfficerId;
    public String stage = "RECEIVED";
    public LocalDate dateReceived;
    public LocalDate targetDate;
    public String referralRef;
    public String referralReason;
    public LocalDate bidValidityExpiry;
    public Instant openedAt;
    public Instant closedAt;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
