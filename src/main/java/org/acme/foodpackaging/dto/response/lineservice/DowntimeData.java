package org.acme.foodpackaging.dto.response.lineservice;

import java.util.Map;

public record DowntimeData(String planningDate, long downtime, Map<String, Long> lines) {}
