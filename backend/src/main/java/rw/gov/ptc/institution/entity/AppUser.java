package rw.gov.ptc.institution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.BaseEntity;
import rw.gov.ptc.institution.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model of a user for the domain. Only profile fields are writable by
 * the application role; identity, status and roles change through the auth
 * module's IdentityAdmin.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(columnDefinition = "uuid", updatable = false, insertable = false)
    public UUID institutionId;
    @Column(updatable = false, insertable = false)
    public String email;
    public String fullName;
    public String jobTitle;
    public String phone;
    public String officeHolderCode;
    public boolean isProcurementOfficer;
    public String locale;
    @Column(updatable = false, insertable = false)
    @Enumerated(EnumType.STRING)
    public UserStatus status;
    @Column(updatable = false, insertable = false)
    public Instant createdAt;

    @Version
    public Long version;
}
