package org.acme.foodpackaging.entity.products;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "PLR_MC", schema = "dbo")
public class PlrMc extends PanacheEntityBase {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "KMC",
        referencedColumnName = "KMC",
        insertable = false,
        updatable = false
    )
    public NsMc ns;

    @Override
    public String toString() {
        return "ProductEntity{" +
                "id=" + id +
                ", kmc='" + kmc + '\'' +
                ", ean13='" + ean13 + '\'' +
                ", type='" + type + '\'' +
                ", glaze='" + glaze + '\'' +
                ", mass='" + mass + '\'' +
                ", filling='" + filling + '\'' +
                ", deletedFlag=" + deletedFlag +
                ", ns=" + ns +
                '}';
    }
}
