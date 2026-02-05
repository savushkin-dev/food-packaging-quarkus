package org.acme.foodpackaging.record;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

public record JobSelection(
        Map<Long, SelectionValue> selection
) {}

