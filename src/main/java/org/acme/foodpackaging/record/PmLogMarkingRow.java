package org.acme.foodpackaging.record;

import java.time.LocalDateTime;

/**
 * record для строк из PM_LOG(F_ID, DTS)
 */
public record PmLogMarkingRow(long fId, LocalDateTime dts) {}
