package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.jobs.PlrPdaynp;
import org.acme.foodpackaging.mapper.DbJobMapper;
import org.acme.foodpackaging.record.DbJobInfo;
import org.acme.foodpackaging.repository.jobs.DbJobInfoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class UpdateJobLoaderService {
    @Inject
    DbJobMapper mapper;
    @Inject
    DbJobInfoRepository jobRepo;

    public Map<Integer, DbJobInfo> loadDbJobInfo(LocalDate planningDay) {

        List<PlrPdaynp> list = jobRepo.findJobsForDay(planningDay, "0119030000");

        return list.stream()
                .filter(r -> r.np != null && r.np != 0)
                .map(mapper::toInfo)
                .collect(Collectors.toMap(DbJobInfo::snpz, j -> j));
    }
}
