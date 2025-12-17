package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.acme.foodpackaging.entity.NS_McEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PLR_PDAYNP", schema = "dbo")
public class JobEntity extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "KSK")
    public String ksk;

    @Column(name = "SNPZ")
    public Integer snpz;

    @Column(name = "DTI")
    public LocalDateTime dti;

    @Column(name = "DTF")
    public LocalDateTime dtf;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "MASSA")
    public Double massa;

    @Column(name = "KOLEV")
    public Integer quantity;

    @Column(name = "NP")
    public BigDecimal np;

    @Column(name = "UX")
    public Integer priority;

    /**
     * Связь на NS_MC (по колонке KMC)
     * insertable/updatable = false
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "KMC", referencedColumnName = "KMC", insertable = false, updatable = false)
    public NS_McEntity mc;
}
