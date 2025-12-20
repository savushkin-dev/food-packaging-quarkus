package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
public class LoadDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

}


