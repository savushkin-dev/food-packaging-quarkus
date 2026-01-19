package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
import jakarta.persistence.*;

public class PlrPev extends PanacheEntityBase {
    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "PEV")
    public Integer maintenanceTypeId;

    @Column(name = "SNM")
    public String maintenanceTypeName;

    @Override
    public String toString() {
        return "PlrPev{" +
                "id=" + id +
                ", maintenanceTypeId=" + maintenanceTypeId +
                ", maintenanceTypeName=" + maintenanceTypeName +
                '}';
    }
}
