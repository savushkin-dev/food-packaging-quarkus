package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.acme.foodpackaging.record.FactProductionRow;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
@Entity
@Table(name = "MS_LOG", schema = "dbo")
@SqlResultSetMapping(
        name = "FactProductionRowMapping",
        classes = @ConstructorResult(
                targetClass = FactProductionRow.class,
                columns = {
                        @ColumnResult(name = "IDBATCH", type = String.class),
                        @ColumnResult(name = "KMC", type = String.class),
                        @ColumnResult(name = "DTV", type = LocalDateTime.class),
                        @ColumnResult(name = "NP", type = Integer.class),
                        @ColumnResult(name = "EVENT", type = Integer.class),
                        @ColumnResult(name = "DT", type = LocalDateTime.class),
                        @ColumnResult(name = "KRC", type = String.class)
                }
        )
)

public class MsLog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "F_ID")
    private Long fId;

    @Column(name = "IDBATCH")
    public String idBatch;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "DTV")
    private LocalDateTime startDateTimeFact;

    @Column(name = "NP")
    public Integer np;

    @Column(name = "EVENT")
    private Integer eventType;

    @Column(name = "DT")
    private LocalDateTime eventTime;

    @Column(name = "KRC", columnDefinition = "CHAR(12)")
    private String lineIdFact;
}
