package org.acme.foodpackaging.dto.request.jobs;

import java.util.Map;

public record JobSelectionRequest(
        Map<Long, SelectionValue> selection
) {
    public record SelectionValue(Boolean isSelect, Boolean isLabeling) {}
}

