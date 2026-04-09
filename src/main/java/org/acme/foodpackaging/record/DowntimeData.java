package org.acme.foodpackaging.record;

import java.util.Map;

public record DowntimeData(String planningDate, long downtime, Map<String, Long> lines) {}
