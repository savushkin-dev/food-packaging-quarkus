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
public class ZinvDto {
    private LocalDate dt;
    private String kpp;
    private String kmc;
    private String ean13;
    private String name;
    private Double sumMass;
}