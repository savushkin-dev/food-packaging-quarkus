package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_SPROG")
public class Sprog {

    @Id
    @Column(name = "SYSN", nullable = false)
    private Double sysn;

    @Column(name = "DT1", nullable = false)
    private LocalDate dt1;

    @Column(name = "DT2", nullable = false)
    private LocalDate dt2;

    @Column(name = "OBJ", nullable = false, length = 10)
    private String obj;

    @Column(name = "NP", nullable = false)
    private Integer np;
}