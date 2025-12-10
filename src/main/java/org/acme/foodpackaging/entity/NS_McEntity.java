package org.acme.foodpackaging.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "NS_MC", schema = "dbo")
public class NS_McEntity extends PanacheEntityBase {

    @Id
    @Column(name = "KMC")
    public String kmc;

    @Column(name = "MASSA")
    public Double massa;

    @Column(name = "EAN13")
    public String ean13;

    @Column(name = "SNM")
    public String shortName;

    @Column(name = "NAME")
    public String name;
}
