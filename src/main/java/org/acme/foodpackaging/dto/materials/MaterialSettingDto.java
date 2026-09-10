package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialSettingDto {
    private String kmt;
    private String snm;
    private String edu;
//    private String edu;
//    private String edu;
    private Boolean inCalc;
}
