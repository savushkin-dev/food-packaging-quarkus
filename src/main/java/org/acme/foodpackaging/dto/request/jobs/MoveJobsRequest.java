package org.acme.foodpackaging.dto.request.jobs;

public record MoveJobsRequest(String fromLineId, String toLineId, int fromIndex, int count, int insertIndex) {}
