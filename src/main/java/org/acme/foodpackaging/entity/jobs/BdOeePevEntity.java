package org.acme.foodpackaging.entity.jobs;

import jakarta.persistence.*;
import org.acme.foodpackaging.dto.DbMaintenanceRow;

import java.sql.Timestamp;
import java.util.UUID;

@SqlResultSetMapping(
        name = "DbMaintenanceRowMapping",
        classes = @ConstructorResult(
                targetClass = DbMaintenanceRow.class,
                columns = {
                        @ColumnResult(name = "F_ID", type = Long.class),
                        @ColumnResult(name = "F_DEL",type = Short.class),
                        @ColumnResult(name = "KRC", type = String.class),
                        @ColumnResult(name = "PDTN", type = Timestamp.class),
                        @ColumnResult(name = "PDTO", type = Timestamp.class),
                        @ColumnResult(name = "PDUR", type = Integer.class),
                        @ColumnResult(name = "SNPZ", type = Long.class),
                        @ColumnResult(name = "NOTE", type = String.class),
                }
        )
)

@Entity
@Table(name = "OEE_PEV", schema = "dbo")
public class BdOeePevEntity {
    @Id
    @Column(name = "F_GUID")
    private UUID id;
}
