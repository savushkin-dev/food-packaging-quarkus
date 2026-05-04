package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

/**
 * One PM_LOG marking row for a batch (F_ID / DTS).
 */
public record PmLogMarkingRow(long fId, LocalDateTime dts) {}
