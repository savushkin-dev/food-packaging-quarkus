package org.acme.foodpackaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "PLR_PLINES", schema = "dbo")
public class LineEntity {

    @Getter
    @Setter
    @Id
    @Column(name = "KRC")
    private String krc;

    @Getter
    @Setter
    @Column(name = "SNM")
    private String snm;

    @Column(name = "F_DEL")
    private Integer fDel;

    public Integer getFDel() { return fDel; }
    public void setFDel(Integer fDel) { this.fDel = fDel; }
}