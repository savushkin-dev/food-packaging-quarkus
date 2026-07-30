package org.acme.foodpackaging.entity.materials;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_MT", schema = "dbo")
public class PlrMt extends PanacheEntityBase {

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

    @Column(name = "KGR", length = 10, nullable = false)
    public String kgr;

    @Column(name = "KMT", length = 10, nullable = false)
    public String kmt;

    @Column(name = "SNM", length = 30, nullable = false)
    public String snm;

    @Column(name = "EDU", length = 10, nullable = false)
    public String edu;

    @Column(name = "PERS", nullable = false)
    public Double pers = 0.0;

    @Column(name = "RND", nullable = false)
    public Double rnd = 0.0;
}