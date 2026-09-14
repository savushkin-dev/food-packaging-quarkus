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
@Table(name = "PLR_ZINV", schema = "dbo")
public class PlrZinv extends PanacheEntityBase {

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

    @Column(name = "EAN13", length = 13)
    public String ean13;

    @Column(name = "NAME", length = 100)
    public String name;

    @Column(name = "SUM_MASS", nullable = false)
    public Double sumMass = 0.0;

    @Column(name = "SUM_KOLEV", nullable = false)
    public Double sumKolev = 0.0;

    @Column(name = "EMK")
    public Double emk;
}