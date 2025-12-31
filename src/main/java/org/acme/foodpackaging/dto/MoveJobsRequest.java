package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MoveJobsRequest {
    private String fromLineId;
    private String toLineId;
    private int fromIndex;
    private int count;
    private int insertIndex;

}
