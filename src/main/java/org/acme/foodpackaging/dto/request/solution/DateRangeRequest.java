package org.acme.foodpackaging.dto.request.solution;

import java.time.LocalDate;

public record DateRangeRequest(LocalDate from, LocalDate to) {}
