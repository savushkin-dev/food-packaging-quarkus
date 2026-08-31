package org.acme.foodpackaging.entity.materials;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_SPROG", schema = "dbo")
public class PlrSprog extends PanacheEntityBase {

    @Id
    @UuidGenerator
    @Column(name = "F_GUID", columnDefinition = "uniqueidentifier")
    public UUID fGuid;

    @Column(name = "F_ID", nullable = false, insertable = false, updatable = false)
    public Long fId;

    @Column(name = "F_TM", nullable = false, insertable = false, updatable = false)
    public byte[] fTm;

    @Column(name = "F_DEL", nullable = false, insertable = false, updatable = false)
    public Integer fDel = 0;

    @Column(name = "SYSN", nullable = false)
    public Double sysn;

    @Column(name = "DT1", nullable = false)
    public LocalDate dt1;

    @Column(name = "DT2", nullable = false)
    public LocalDate dt2;

    @Column(name = "OBJ", length = 10, nullable = false)
    public String obj;

    @Column(name = "NP", nullable = false)
    public Integer np;
}