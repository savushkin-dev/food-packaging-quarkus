package org.acme.foodpackaging.entity.products;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "PLR_CHANGE", schema = "dbo")
public class PlrChange extends PanacheEntityBase {
    @Id
    @Column(name = "F_GUID", nullable = false)
    public UUID id;

    @Column(name = "NPAR")
    public String parameter;

    @Column(name = "FROM_VALUE")
    public String from;

    @Column(name = "TO_VALUE")
    public String to;

    @Column(name = "DUR")
    public Integer duration;

    @Column(name = "F_DEL")
    public Integer deletedFlag;

    @Column(name = "KRC")
    public String lineId;

    @Column(name = "CLEAN")
    public boolean isPLRLC;

    @Override
    public String toString() {
        return "CleaningRuleEntity{" +
                "id=" + id +
                ", parameter='" + parameter + '\'' +
                ", fromValue='" + from + '\'' +
                ", toValue='" + to + '\'' +
                ", duration=" + duration +
                ", deletedFlag=" + deletedFlag +
                ", lineId='" + lineId + '\'' +
                ", isPLRLC=" + isPLRLC +
                '}';
    }
}
