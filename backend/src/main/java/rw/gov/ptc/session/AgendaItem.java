package rw.gov.ptc.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** An agenda item; always a file of a permitted type, or a type I administrative item. */
@Entity
@Table(name = "agenda_item")
public class AgendaItem extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(1)")
    public String itemType;
    public int position;
    public String adminKind;
    public String adminNote;
    public String status = "PENDING";
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
