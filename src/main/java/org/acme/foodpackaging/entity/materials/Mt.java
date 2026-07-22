package org.acme.foodpackaging.entity.materials;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "PLR_MT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mt {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "plr_mt_seq")
    @SequenceGenerator(
            name = "plr_mt_seq",
            sequenceName = "plr_mt_seq",
            allocationSize = 20
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "KGR", length = 10, nullable = false)
    private String kgr;

    @Column(name = "KMT", length = 10, nullable = false)
    private String kmt;

    @Column(name = "SNM", length = 30)
    private String snm;

    @Column(name = "PERS", precision = 10, scale = 2)
    private BigDecimal pers;

    @Column(name = "RND", precision = 10, scale = 2)
    private BigDecimal rnd;
}