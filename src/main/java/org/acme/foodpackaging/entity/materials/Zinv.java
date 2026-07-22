package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "PLR_ZINV")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Zinv implements Serializable {

    @Id
    @Column(name = "DT", nullable = false)
    private LocalDate dt;

    @Id
    @Column(name = "KPP", length = 10, nullable = false)
    private String kpp;

    @Id
    @Column(name = "KMC", length = 10, nullable = false)
    private String kmc;

    @Column(name = "EAN13", length = 20)
    private String ean13;

    @Column(name = "NAME", length = 50)
    private String name;

    @Column(name = "SUM_MASS", nullable = false)
    private Double sumMass;
}