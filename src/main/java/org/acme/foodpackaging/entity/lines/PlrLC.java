package org.acme.foodpackaging.entity.lines;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PLR_LC", schema = "dbo")
@Getter
@Setter
public class PlrLC {

    @Id
    @Column(name = "F_GUID", nullable = false)
    private UUID id;

    @Column(name = "KRC")
    private String lineId;

    @Column(name = "CLEAN")
    private Integer additionalCleaning;

    @Column(name = "DTBEG")
    private LocalDate dtBegin;

    @Column(name = "DTEND")
    private LocalDate dtEnd;

    @Column(name = "F_DEL")
    private Integer fDel;
}
