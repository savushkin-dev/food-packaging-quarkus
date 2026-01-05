package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.acme.foodpackaging.dto.DbMaintenanceRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "OEE_PEV", schema = "dbo")
@SqlResultSetMapping(
        name = "DbMaintenanceRowMapping",
        classes = @ConstructorResult(
                targetClass = DbMaintenanceRow.class,
                columns = {
                        @ColumnResult(name = "F_ID", type = Long.class),
                        @ColumnResult(name = "F_DEL", type = Short.class),
                        @ColumnResult(name = "KRC", type = String.class),
                        @ColumnResult(name = "PDTN", type = Timestamp.class),
                        @ColumnResult(name = "PDTO", type = Timestamp.class),
                        @ColumnResult(name = "PDUR", type = Integer.class),
                        @ColumnResult(name = "SNPZ", type = Long.class),
                        @ColumnResult(name = "NOTE", type = String.class),
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
    private LocalDateTime startProductionDateTime;

    @Column(name = "PDTO")
    private LocalDateTime endDateTime;

    @Column(name = "PDUR")
    private Integer duration;

    @Column(name = "SNPZ")
    private Long snpz;

    @Column(name = "EVTYPE")
    private Integer evtype;

    @Column(name = "REASON")
    private Integer reason;

    @Column(name = "NOTE")
    private String note;

    @Override
    public String toString() {
        return "OeePev{" +
                "fId=" + fId +
                ", fDel=" + fDel +
                ", lineId='" + lineId + '\'' +
                ", startProductionDateTime=" + startProductionDateTime +
                ", endDateTime=" + endDateTime +
                ", duration=" + duration +
                ", snpz=" + snpz +
                ", evtype=" + evtype +
                ", reason=" + reason +
                ", note='" + note + '\'' +
                '}';
    }

}
