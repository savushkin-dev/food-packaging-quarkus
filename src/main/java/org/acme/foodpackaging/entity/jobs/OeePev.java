package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.*;
import org.acme.foodpackaging.dto.oeepev.CleaningRow;
import org.acme.foodpackaging.dto.oeepev.DelayRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;

import java.time.LocalDateTime;
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "OEE_PEV", schema = "dbo")

@SqlResultSetMapping(
        name = "MaintenanceRowMapping",
        classes = @ConstructorResult(
                targetClass = MaintenanceRow.class,
                columns = {
                        @ColumnResult(name = "F_ID", type = Long.class),
                        @ColumnResult(name = "KRC", type = String.class),
                        @ColumnResult(name = "NOTE", type = String.class),
                        @ColumnResult(name = "PDTN", type = LocalDateTime.class),
                        @ColumnResult(name = "PDUR", type = Integer.class),
                        @ColumnResult(name = "EVTYPE", type = Integer.class)
                }
        )
)

@SqlResultSetMapping(
        name = "DelayRowMapping",
        classes = @ConstructorResult(
                targetClass = DelayRow.class,
                columns = {
                        @ColumnResult(name = "F_ID", type = Long.class),
                        @ColumnResult(name = "SNPZ", type = Long.class),
                        @ColumnResult(name = "NOTE", type = String.class),
                        @ColumnResult(name = "PDUR", type = Integer.class)
                }
        )
)

@SqlResultSetMapping(
        name = "CleaningRowMapping",
        classes = @ConstructorResult(
                targetClass = CleaningRow.class,
                columns = {
                        @ColumnResult(name = "F_ID", type = Long.class),
                        @ColumnResult(name = "SNPZ", type = Long.class)
                }
        )
)
public class OeePev extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "F_ID")
    private Long fId;

    @Column(name = "F_DEL")
    private short fDel;

    @Column(name = "KRC", columnDefinition = "CHAR(12)")
    private String lineId;

    @Column(name = "PDTN")
    private LocalDateTime startDateTime;

    @Column(name = "PDTO")
    private LocalDateTime endDateTime;

    @Column(name = "PDUR")
    private Integer duration;

    @Column(name = "SNPZ")
    private Long snpz;

    @Column(name = "EVTYPE")
    private Integer eventTypeId;

    @Column(name = "REASON")
    private Integer reason;

    @Column(name = "NOTE")
    private String note;
}
