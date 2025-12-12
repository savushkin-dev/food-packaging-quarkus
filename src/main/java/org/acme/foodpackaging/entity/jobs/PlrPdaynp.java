package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PLR_PDAYNP")
public class PlrPdaynp extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID")
    public UUID id;

    @Column(name = "SNPZ")
    public Integer snpz;

    @Column(name = "NP")
    public Integer np;

    @Column(name = "KOLEV")
    public Integer quantity;

    @Column(name = "UX")
    public Integer priority;

    @Column(name = "MASSA")
    public Double mass;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "DTI")
    public LocalDateTime dti;

    @Column(name = "DTF")
    public LocalDateTime dtf;

    @Column(name = "SNM")
    public String shortName;

    @Column(name = "KSK")
    public String ksk;
}
