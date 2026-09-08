package org.acme.foodpackaging.dto.request.lines;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PinRequest {
    private String lineId;
    private Integer pinCount;
    private Boolean pinAll;

}
