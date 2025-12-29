package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.lines.LineEntity;

import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<LineEntity> {
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
}
