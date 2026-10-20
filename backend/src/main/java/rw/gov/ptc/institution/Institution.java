package rw.gov.ptc.institution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "institution")
public class Institution extends BaseEntity {

    public String name;
    public String abbreviation;
    public String institutionType;
    public String supervisingAuthority;
    public String minutesLetterhead;
    public String minutesFontFamily = "Calibri";
    public int minutesFontSize = 11;

    @JsonIgnore
    public byte[] minutesTemplateDocx;

    public boolean holidayOverride;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(3)")
    public String defaultCurrency = "RWF";
    public String status = "ACTIVE";
    public Instant createdAt = Instant.now();

    @Version
    public Long version;

    public boolean isHasMinutesTemplate() {
        return minutesTemplateDocx != null && minutesTemplateDocx.length > 0;
    }
}
