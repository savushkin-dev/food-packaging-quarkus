package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.acme.foodpackaging.persistence.converter.BooleanToIntegerConverter;
import org.acme.foodpackaging.record.DbJobRow;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "BD_VZPMC", schema = "dbo")
@SqlResultSetMapping(
        name = "DbJobRowMapping",
        classes = @ConstructorResult(
                targetClass = DbJobRow.class,
                columns = {
                        @ColumnResult(name = "DTI", type = LocalDateTime.class),
                        @ColumnResult(name = "KMC", type = String.class),
                        @ColumnResult(name = "NP", type = Integer.class),
                        @ColumnResult(name = "KOLEV", type = Integer.class),
                        @ColumnResult(name = "MASSA", type = double.class),
                        @ColumnResult(name = "PDTN", type = LocalDateTime.class),
                        @ColumnResult(name = "PDTO", type = LocalDateTime.class),
                        @ColumnResult(name = "PDUR", type = Integer.class),
                        @ColumnResult(name = "SNPZ", type = Long.class),
                        @ColumnResult(name = "UX", type = Integer.class),
                        @ColumnResult(name = "KRC", type = String.class),
                        @ColumnResult(name = "SNM", type = String.class),
                        @ColumnResult(name = "EMK", type = Integer.class),
                        @ColumnResult(name = "KOLMP", type = Integer.class),
                        @ColumnResult(name = "STICKER", type =  Integer.class),
                }
        )
)
public class BdVzpmc extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "KMC")
    public String kmc;

    @Column(name = "DTI")
    public LocalDateTime startDateTime;

    @Column(name = "NP")
    public Integer np;

    @Column(name = "KOLEV")
    public Integer quantity;

    @Column(name = "UX")
    public Integer priority;

    @Column(name = "SNPZ")
    public Long snpz;

    @Column(name = "MASSA")
    public Double mass;

    @Column(name = "SNM")
    public String shortName;

    @Column(name = "KRC", columnDefinition = "CHAR(12)")
    private String lineId;

    @Column(name = "PDTN")
    private LocalDateTime startProductionDateTime;

    @Column(name = "PDTO")
    private LocalDateTime endDateTime;

    @Column(name = "PDUR")
    private Integer duration;

    @Column(name = "EMK")
    private Integer emk;

    @Column(name = "KOLMP")
    private Integer placePlan;

    @Convert(converter = BooleanToIntegerConverter.class)
    @Column(name = "STICKER")
    private Boolean isHandPackaging;

    @Override
    public String toString() {
        return "BdVzpmc{" +
                "id=" + id +
                ", kmc='" + kmc + '\'' +
                ", startDateTime=" + startDateTime +
                ", np=" + np +
                ", quantity=" + quantity +
                ", priority=" + priority +
                ", snpz=" + snpz +
                ", mass=" + mass +
                ", shortName='" + shortName + '\'' +
                ", lineId='" + lineId + '\'' +
                ", startProductionDateTime=" + startProductionDateTime +
                ", endDateTime=" + endDateTime +
                ", duration=" + duration +
                ", emk=" + emk +
                ", placePlan=" + placePlan +
                ", isHandPackaging=" + isHandPackaging +
                '}';
    }
}

