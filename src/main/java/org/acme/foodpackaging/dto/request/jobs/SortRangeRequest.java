package org.acme.foodpackaging.dto.request.jobs;

public record SortRangeRequest(int fromIndex, int sortCount, String lineId, boolean sortUp) {}
