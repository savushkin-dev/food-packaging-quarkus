package org.acme.foodpackaging.entity.lines;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "PLR_PLINES", schema = "dbo")
@Getter
@Setter
public class PlrLines {

    @Id
    @Column(name = "F_GUID", nullable = false)
    private UUID id;

    @Column(name = "KRC")
    private String lineId;

    @Column(name = "SNM")
    private String snm;

    @Column(name = "GRF")
    private String type;

    @Column(name = "PROD")
    private Integer speed;

    @Column(name = "MPROD")
    private Integer handPackagingSpeed;

    @Column(name = "F_DEL")
    private Integer fDel;

    @Override
    public String toString() {
        return "PlrLines{" +
                "id=" + id +
                ", lineId='" + lineId + '\'' +
                ", snm='" + snm + '\'' +
                ", type='" + type + '\'' +
                ", speed=" + speed +
                ", handPackagingSpeed=" + handPackagingSpeed +
                ", fDel=" + fDel +
                '}';
    }
}
