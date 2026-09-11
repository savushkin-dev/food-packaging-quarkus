package org.acme.foodpackaging.dto.request.jobs;

public record DelayNoteRequest(String lineId, int index, String delayNote) {}
