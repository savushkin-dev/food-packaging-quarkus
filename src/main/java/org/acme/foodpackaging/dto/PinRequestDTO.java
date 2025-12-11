package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PinRequestDTO {
    private String lineId;
    private Integer pinCount;
    private Boolean pinAll;

}
