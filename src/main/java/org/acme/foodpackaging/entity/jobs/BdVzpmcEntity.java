package org.acme.foodpackaging.entity.jobs;

import jakarta.persistence.*;
import org.acme.foodpackaging.record.DbJobRow;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@SqlResultSetMapping(
        name = "DbJobRowMapping",
        classes = @ConstructorResult(
                targetClass = DbJobRow.class,
                columns = {
                        @ColumnResult(name = "DTI", type = Timestamp.class),
                        @ColumnResult(name = "KMC", type = String.class),
                        @ColumnResult(name = "NP", type = Integer.class),
                        @ColumnResult(name = "KOLEV", type = Integer.class),
                        @ColumnResult(name = "MASSA", type = double.class),
                        @ColumnResult(name = "PDTN", type = Timestamp.class),
                        @ColumnResult(name = "PDTO", type = Timestamp.class),
                        @ColumnResult(name = "PDUR", type = Integer.class),
                        @ColumnResult(name = "SNPZ", type = Long.class),
                        @ColumnResult(name = "UX", type = Integer.class),
                        @ColumnResult(name = "KRC", type = String.class),
                        @ColumnResult(name = "SNM", type = String.class),
                }
        )
)
@Entity
@Table(name = "BD_VZPMC", schema = "dbo")
public class BdVzpmcEntity {

    @Id
    @Column(name = "F_GUID")
    private UUID id;
}

