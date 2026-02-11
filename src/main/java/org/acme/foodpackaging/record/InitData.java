package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.PackagingSchedule;

import java.util.Map;

public record InitData (
 PackagingSchedule schedule, Map<Long, DbJobRow> jobsFromDbRow
){}

