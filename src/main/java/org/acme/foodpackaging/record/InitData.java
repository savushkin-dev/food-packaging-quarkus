package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.PackagingSchedule;

import java.util.List;

public record InitData (
        PackagingSchedule schedule, List<DbJobRow> jobsFromDbRow
){}

