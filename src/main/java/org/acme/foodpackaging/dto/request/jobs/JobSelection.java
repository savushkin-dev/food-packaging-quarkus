package org.acme.foodpackaging.dto.request.jobs;

import java.util.Map;

public record JobSelection(
        Map<Long, SelectionValue> selection
) {}

