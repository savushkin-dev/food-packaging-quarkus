package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithMaterialsDto {
    private LocalDate dt;
    private String kpp;
    private String kmc;
    private String kt;
    private String ean13;
    private Double emk;
    private String name;
    private Double sumMass;
    private Double sumKolev;
    private List<SinvDto> materials;
}