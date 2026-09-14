package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.lines.PlrLines;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<PlrLines> {
    /**
     * Загружает id и название линии
     */
    public ConcurrentMap<String, String> loadLines() {
        return find("fDel = 0 and snm is not null")
                .stream()
                .collect(Collectors.toConcurrentMap(
                        e -> e.getLineId().trim(),
                        e -> e.getSnm().trim(),
                        (existing, ignored) -> existing
                ));
    }

    public Optional<PlrLines> findLineInfo(String lineId) {
        return find("lineId = ?1 and snm is not null", lineId)
                .firstResultOptional();
    }
}
