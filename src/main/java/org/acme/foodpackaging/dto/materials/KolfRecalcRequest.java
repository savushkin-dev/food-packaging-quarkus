package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KolfRecalcRequest {
    private String date;
    private String kpp;
    private String kmt;
    private Double kolf;
    private List<ProductWithMaterialsDto> data;
}