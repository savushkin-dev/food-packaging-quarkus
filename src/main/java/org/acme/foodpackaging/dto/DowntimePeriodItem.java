package org.acme.foodpackaging.dto;

import java.time.LocalDateTime;

public record DowntimePeriodItem(LocalDateTime dtStart, LocalDateTime dtEnd) {}
