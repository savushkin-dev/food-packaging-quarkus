package org.acme.foodpackaging.dto;

import lombok.Getter;
import java.util.Map;

@Getter
public class DowntimeResponse {
    public String dt;
    public long downtime;
    public Map<String, Long> lines;

    public DowntimeResponse(String dt, long downtime, Map<String, Long> lines) {
        this.dt = dt;
        this.downtime = downtime;
        this.lines = lines;
    }
}