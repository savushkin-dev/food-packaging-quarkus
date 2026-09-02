package org.acme.foodpackaging.repository.lines;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.dto.plrlc.EquipmentPeriodDto;
import org.acme.foodpackaging.entity.lines.PlrLC;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlrLcRepository implements PanacheRepository<PlrLC> {
    public Map<String, Integer> loadLinesCleaning() {
        return find("fDel = 0 and lineId is not null")
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getLineId() == null ? "" : e.getLineId().trim(),
                        e -> e.getAdditionalCleaning() == null ? 0 : e.getAdditionalCleaning(),
                        (existing, ignored) -> existing
                ));
    }

    public List<EquipmentPeriodDto> loadEquipmentPeriods() {
        return find("lineId is not null and dtBegin is not null")
                .stream()
                .map(e -> new EquipmentPeriodDto(
                        e.getLineId().trim(),
                        e.getDtBegin(),
                        e.getDtEnd()
                ))
                .toList();
    }
}
