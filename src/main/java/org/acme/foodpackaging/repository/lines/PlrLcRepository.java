package org.acme.foodpackaging.repository.lines;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.lines.PlrLC;
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
}
