package org.acme.foodpackaging.entity.materials;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_PP", schema = "dbo")
public class Pp extends PanacheEntityBase {

    @Id
    @UuidGenerator
    @Column(name = "F_GUID", columnDefinition = "uniqueidentifier")
    public UUID fGuid;

    @Column(name = "F_ID", nullable = false, insertable = false, updatable = false)
    public Long fId;

    @Column(name = "F_TM", nullable = false, insertable = false, updatable = false)
    public byte[] fTm;

    @Column(name = "F_DEL", nullable = false, insertable = false, updatable = false)
    public Integer fDel = 0;

    @Column(name = "KPP", length = 8, nullable = false)
    public String kpp;

    @Column(name = "SNM", length = 30, nullable = false)
    public String snm;
}