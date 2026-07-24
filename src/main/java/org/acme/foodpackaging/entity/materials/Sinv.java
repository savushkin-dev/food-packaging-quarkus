package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "PLR_SINV")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sinv implements Serializable {

    @Id
    @Column(name = "DT", nullable = false)
    private LocalDate dt;

    @Id
    @Column(name = "KPP", length = 10, nullable = false)
    private String kpp;

    @Id
    @Column(name = "KMC", length = 10, nullable = false)
    private String kmc;

    @Id
    @Column(name = "KT", length = 10, nullable = false)
    private String kt;

    @Id
    @Column(name = "KMT", length = 10, nullable = false)
    private String kmt;

    @Column(name = "NORM", nullable = false)
    private Double norm;

    @Column(name = "NORMF", nullable = false)
    private Double normf;

    @Column(name = "KOLF", nullable = false)
    private Double kolf;
}