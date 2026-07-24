package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "PLR_RNPP",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rnpp_business",
                        columnNames = {"ID, SYSN", "KMC", "KT", "EMK", "KKOM"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rnpp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rnpp_seq")
    @SequenceGenerator(name = "rnpp_seq", sequenceName = "rnpp_seq", allocationSize = 20)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SYSN", nullable = false)
    private Double sysn;

    @Column(name = "KMC", length = 10, nullable = false)
    private String kmc;

    @Column(name = "KT", length = 10, nullable = false)
    private String kt;

    @Column(name = "EMK", nullable = false)
    private Double emk;

    @Column(name = "KKOM", length = 10, nullable = false)
    private String kkom;

    @Column(name = "KOL1T", nullable = false)
    private Double kol1t;

    @Column(name = "KOLVK", nullable = false)
    private Double kolvk;
}