package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.WorkCalendar;

import java.util.List;
import java.util.Map;

public record FrontendDataWrapper(
        List<Job> jobs,
        Map<Long, DbJobRow> dbJobRowMap,
        WorkCalendar workCalendar
) {}

