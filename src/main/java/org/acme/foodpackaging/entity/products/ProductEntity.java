package org.acme.foodpackaging.entity.products;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.acme.foodpackaging.entity.NS_McEntity;

import java.util.UUID;

@Entity
@Table(name = "PLR_MC", schema = "dbo")
public class ProductEntity extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID")
    public UUID id;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "EAN13")
    public String ean13;

    @Column(name = "GRF")
    public String type;

    @Column(name = "TGLAZ")
    public String glaze;

    @Column(name = "TMASS")
    public String mass;

    @Column(name = "TFBF")
    public String filling;

    @Column(name = "F_DEL")
    public Integer deletedFlag;

    @OneToOne
    @JoinColumn(name = "KMC", referencedColumnName = "KMC", insertable = false, updatable = false)
    public NS_McEntity ns;
}
