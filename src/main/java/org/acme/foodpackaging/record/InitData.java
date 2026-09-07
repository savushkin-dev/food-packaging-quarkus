package org.acme.foodpackaging.record;

import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;

import java.util.List;

public record InitData (
        PackagingSchedule schedule, List<JobRow> jobsFromDbRow
){}

