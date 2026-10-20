package rw.gov.ptc.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A signed contract, referenced from Umucyo by contract_ref. Values in the contract currency. */
@Entity
@Table(name = "contract")
public class Contract extends TenantEntity {

    public String contractRef;
    public String tenderRef;
    public String title;
    public String supplierRef;
    public String procurementType;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(3)")
    public String currency;
    public BigDecimal originalValue;
    public LocalDate signedDate;
    public LocalDate originalCompletionDate;
    public LocalDate currentCompletionDate;
    public String perfSecurityRef;
    public BigDecimal perfSecurityAmount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(3)")
    public String perfSecurityCurrency;
    public LocalDate perfSecurityExpiry;
    public boolean isFramework;
    public LocalDate frameworkExpiry;
    public String status = "ACTIVE";
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
