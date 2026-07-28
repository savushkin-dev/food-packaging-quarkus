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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_SINV", schema = "dbo")
public class Sinv extends PanacheEntityBase {

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

    @Column(name = "DT", nullable = false)
    public LocalDate dt;

    @Column(name = "KPP", length = 10, nullable = false)
    public String kpp;

    @Column(name = "KMC", length = 10, nullable = false)
    public String kmc;

    @Column(name = "KT", length = 10, nullable = false)
    public String kt;

    @Column(name = "KMT", length = 10, nullable = false)
    public String kmt;

    @Column(name = "NORM", nullable = false)
    public Double norm = 0.0;

    @Column(name = "NORMF", nullable = false)
    public Double normf = 0.0;

    @Column(name = "KOLF", nullable = false)
    public Double kolf = 0.0;
}