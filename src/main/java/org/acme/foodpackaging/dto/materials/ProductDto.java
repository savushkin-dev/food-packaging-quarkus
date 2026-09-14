package org.acme.foodpackaging.dto.materials;

import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {
    private String kmc;
    private String kt;
    private String ean13;
    private Double emk;
    private Double sumMass;
    private Double sumKolev;
    private String productName;
    private Double krkmc;

}