package org.acme.foodpackaging.record;

import java.util.Map;

public record JobSelection(
        Map<Long, Boolean> selection
) {}

