package rw.gov.ptc.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.session.enums.ActivityStatus;

import java.time.Instant;
import java.util.UUID;

/** What the committee does with a file in a session (e.g. preliminary examination), and whether it was done. */
@Entity
@Table(name = "agenda_activity")
public class AgendaActivity extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID agendaItemId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String activityCode;
    public int position;
    @Enumerated(EnumType.STRING)
    public ActivityStatus status = ActivityStatus.PLANNED;
    public String note;
    public Instant recordedAt;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    @Column(columnDefinition = "uuid")
    public UUID carriedFromId;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
