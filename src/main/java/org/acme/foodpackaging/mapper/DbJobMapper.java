package org.acme.foodpackaging.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.PlrPdaynp;
import org.acme.foodpackaging.record.DbJobInfo;

@ApplicationScoped
public class DbJobMapper {
    public DbJobInfo toInfo(PlrPdaynp e) {

        return new DbJobInfo(
                e.snpz, e.np,
                e.quantity, e.priority, e.mass,
                e.shortName, e.kmc,
                e.dti, e.dtf
        );
    }
}
