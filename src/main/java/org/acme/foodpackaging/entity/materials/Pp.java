package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "PLR_PP")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pp {

    @Id
    @Column(name = "KPP", length = 8, nullable = false)
    private String kpp;

    @Column(name = "SNM", length = 30)
    private String snm;
}