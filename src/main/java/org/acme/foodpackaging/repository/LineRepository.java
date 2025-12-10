package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.lines.LineEntity;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<LineEntity> {
    /**
     * Загружает id и название линии
     */
    public ConcurrentMap<String, String> loadLines() {
        List<LineEntity> lines = list("fDel = 0");

        return lines.stream()
                .collect(Collectors.toConcurrentMap(
                        e -> e.getKrc().trim(),
                        e -> e.getSnm().trim(),
                        (existing, replacement) -> existing
                ));
    }
}
