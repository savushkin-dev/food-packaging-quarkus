package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.PlrPev;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlrPevRepository implements PanacheRepository<PlrPev>  {

    public ConcurrentMap<Integer, String> loadMaintenanceTypesRowMap() {
        return find("fDel = 0 and maintenanceTypeId is not null and maintenanceTypeName is not null").stream()
                .collect(Collectors.toConcurrentMap(
                        plrPev -> plrPev.maintenanceTypeId,
                        plrPev -> plrPev.maintenanceTypeName,
                        (existing, ignored) -> existing
                ));
    }
}
