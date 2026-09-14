package org.acme.foodpackaging.dto.request.solution;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record LoadRequest(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate startDate,
        String version
) {}
