package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinvDto {
    private LocalDate dt;
    private String kpp;
    private String kmc;
    private String kt;
    private String snmKt; //Наименование упаковки
    private String kmt;
    private String snmMt; //Наименование материала
    private Double norm;
    private Double normf;
    private Double kolf;
}