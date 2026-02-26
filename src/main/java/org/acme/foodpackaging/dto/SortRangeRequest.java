package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SortRangeRequest {
    private int fromIndex;
    private int sortCount;
    private String lineId;
    private boolean sortUp;
}
