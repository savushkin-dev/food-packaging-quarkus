package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "OEE_PEV", schema = "dbo")
public class OeePevEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "F_ID")
    private long fId;

    @Column(name = "KRC", columnDefinition = "CHAR(12)")
    private String krc;

    @Column(name = "PDTN")
    private LocalDateTime pdtn;

    @Column(name = "PDTO")
    private LocalDateTime pdto;

    @Column(name = "PDUR")
    private Integer pdur;

    @Column(name = "SNPZ")
    private Integer snpz;

    @Column(name = "EVTYPE")
    private Integer evtype;

    @Column(name = "REASON")
    private Integer reason;

    @Column(name = "NOTE")
    private String note;


}
