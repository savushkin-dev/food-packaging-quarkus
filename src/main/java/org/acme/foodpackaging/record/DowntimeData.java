package org.acme.foodpackaging.record;

import java.util.Map;

public record DowntimeData(String from, String to, long downtime, Map<String, Long> lines) {}
