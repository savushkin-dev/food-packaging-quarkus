package org.acme.foodpackaging.entity.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "PLR_PEV", schema = "dbo")
public class PlrPev extends PanacheEntityBase {
    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "PEV")
    public Integer maintenanceTypeId;

    @Column(name = "SNM")
    public String maintenanceTypeName;

    @Column(name = "F_DEL")
    public Integer fDel;

    @Override
    public String toString() {
        return "PlrPev{" +
                "id=" + id +
                ", maintenanceTypeId=" + maintenanceTypeId +
                ", maintenanceTypeName=" + maintenanceTypeName +
                ", fDel=" + fDel +
                '}';
    }
}
