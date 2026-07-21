package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_RNPP",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"SYSN", "KMC", "EMK", "KKOM", "KOL1T", "KOLVK"})
        })
public class Rnpp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rnpp_seq")
    @SequenceGenerator(name = "rnpp_seq", sequenceName = "rnpp_seq", allocationSize = 20)
    private Long id;

    @Column(name = "SYSN", nullable = false)
    private Double sysn;

    @Column(name = "KMC", nullable = false, length = 10)
    private String kmc;

    @Column(name = "EMK", nullable = false)
    private Double emk;

    @Column(name = "KKOM", nullable = false, length = 10)
    private String kkom;

    @Column(name = "KOL1T", nullable = false)
    private Double kol1t = 0.0;

    @Column(name = "KOLVK", nullable = false)
    private Double kolvk = 0.0;
}