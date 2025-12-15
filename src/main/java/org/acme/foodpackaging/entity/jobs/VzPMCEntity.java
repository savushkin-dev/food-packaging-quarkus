package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "BD_VZPMC", schema = "dbo")
public class VzPMCEntity extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "DTI")
    public LocalDateTime dti;

    @Column(name = "NP")
    public Integer np;

    @Column(name = "KOLEV")
    public Integer quantity;

    @Column(name = "UX")
    public Integer priority;

    @Column(name = "SNPZ")
    public Integer snpz;

    @Column(name = "MASSA")
    public Double mass;

//    @Column(name = "SNM")
//    public String shortName;

    @Column(name = "KRC", columnDefinition = "CHAR(12)")
    private String krc;

    @Column(name = "PDTN")
    private LocalDateTime pdtn;

    @Column(name = "PDTO")
    private LocalDateTime pdto;

    @Column(name = "PDUR")
    private Integer pdur;
}

