package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.Product;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Parameters for creating a regular production job.
 */
public record ProductionJobParams(
        String id,
        String lineId,
        String name,
        Long snpz,
        int np,
        int quantity,
        int priority,
        double mass,
        Product product,
        Duration duration,
        LocalDateTime startProductionDateTime,
        int emk
) {}