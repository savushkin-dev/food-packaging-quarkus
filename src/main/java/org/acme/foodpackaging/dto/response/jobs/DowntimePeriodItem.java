package org.acme.foodpackaging.dto.response.jobs;

import java.time.LocalDateTime;

/**
 * DTO-record для одного интервала простоя
 */

public record DowntimePeriodItem(LocalDateTime dtStart, LocalDateTime dtEnd) {}
