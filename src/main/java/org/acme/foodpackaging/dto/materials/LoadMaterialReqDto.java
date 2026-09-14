package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoadMaterialReqDto {

    private String date;
    private String kpp;

}
