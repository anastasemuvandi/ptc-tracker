package rw.gov.ptc.institution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.BaseEntity;

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
    public String status;
    @Column(updatable = false, insertable = false)
    public Instant createdAt;

    @Version
    public Long version;
}
