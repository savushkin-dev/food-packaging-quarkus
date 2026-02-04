package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.acme.foodpackaging.record.FactProductionRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
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
                        @ColumnResult(name = "DTV", type = Timestamp.class),
                        @ColumnResult(name = "NP", type = Integer.class),
                        @ColumnResult(name = "EVENT", type = Integer.class),
                        @ColumnResult(name = "DT", type = Timestamp.class),
                        @ColumnResult(name = "KRC", type = String.class)
                }
        )
)

public class MsLog extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Id
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

    @Override
    public String toString() {
        return "MsLog{" +
                "id=" + id +
                ", idBatch='" + idBatch + '\'' +
                ", kmc='" + kmc + '\'' +
                ", startDateTimeFact=" + startDateTimeFact +
                ", np=" + np +
                ", eventType=" + eventType +
                ", eventTime=" + eventTime +
                ", lineId='" + lineIdFact + '\'' +
                '}';
    }
}
