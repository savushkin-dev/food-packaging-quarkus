package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.acme.foodpackaging.record.CameraFactRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "PM_LOG", schema = "dbo")
@SqlResultSetMapping(
        name = "CameraFactRowMapping",
        classes = @ConstructorResult(
                targetClass = CameraFactRow.class,
                columns = {
                        @ColumnResult(name = "DTSTART", type = Timestamp.class),
                        @ColumnResult(name = "DTEND", type = Timestamp.class)
                }
        )
)

public class PmLog extends PanacheEntityBase {

    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "DTS")
    private LocalDateTime cameraStart;

    @Override
    public String toString() {
        return "PmLog{" +
                "id=" + id +
                ", cameraStart=" + cameraStart +
                '}';
    }
}
