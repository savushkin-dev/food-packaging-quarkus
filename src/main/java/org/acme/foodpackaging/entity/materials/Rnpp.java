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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_RNPP", schema = "dbo")
public class Rnpp extends PanacheEntityBase {

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

    @Column(name = "KMC", length = 10, nullable = false)
    public String kmc;

    @Column(name = "KT", length = 10, nullable = false)
    public String kt;

    @Column(name = "EMK", nullable = false)
    public Double emk;

    @Column(name = "KKOM", length = 10, nullable = false)
    public String kkom;

    @Column(name = "KOL1T", nullable = false)
    public Double kol1t = 0.0;

    @Column(name = "KOLVK", nullable = false)
    public Double kolvk = 0.0;
}