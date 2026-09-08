package org.acme.foodpackaging.dto.request.solution;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class LoadRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    private String version;
}


