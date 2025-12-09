package org.acme.foodpackaging.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "PLR_PLINES", schema = "dbo")
public class LineSpeedEntity {

    @Id
    @Column(name = "KRC")
    private String line;

    @Column(name = "GRF")
    private String type;

    @Column(name = "PROD")
    private Integer speed;
}
