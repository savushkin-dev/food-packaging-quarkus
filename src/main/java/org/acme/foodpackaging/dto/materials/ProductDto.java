package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String productName;


}