package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DelayNoteRequest {
    private String lineId;
    private int index;
    private String delayNote;
}
