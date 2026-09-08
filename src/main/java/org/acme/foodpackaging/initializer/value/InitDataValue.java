package org.acme.foodpackaging.initializer.value;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.row.jobs.JobRow;

import java.util.List;

public record InitDataValue (
        PackagingSchedule schedule, List<JobRow> jobsFromDbRow
){}

