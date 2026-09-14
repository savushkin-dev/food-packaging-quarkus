package org.acme.foodpackaging.dto.request.lines;

public record PinRequest(String lineId, Integer pinCount, Boolean pinAll) {}
