package org.acme.foodpackaging.dto;

import java.util.Map;

public record JobSelectionDTO(
        Map<Integer, Boolean> selection
) {}

