package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.Product;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Parameters for creating a maintenance job with time constraints.
 */
public record MaintenanceJobParams(
        String id,
        String lineId,
        String name,
        String note,
        Integer typeId,
        Product product,
        Duration duration,
        int priority,
        boolean pinned,
        LocalDateTime startProductionDateTime
) {}

