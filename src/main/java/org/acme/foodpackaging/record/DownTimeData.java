package org.acme.foodpackaging.record;

import java.time.Duration;
import java.util.Map;

public record DownTimeData(long commonDownTime, Map<String, Duration> lineDonwTimes) {}