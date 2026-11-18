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
public class SaveRequest {
    private String date;
    private String kpp;
    private String type;
    private String userId;
    private String userFio;
    private List<ProductWithMaterialsDto> data;
}