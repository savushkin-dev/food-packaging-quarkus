package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PLR_PDAYNP", schema = "dbo")
public class PDayEntity extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "KSK")
    public String ksk;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "DTI")
    public LocalDateTime dti;

    @Column(name = "DTF")
    public LocalDateTime dtf;

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

}

