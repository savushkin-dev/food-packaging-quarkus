package org.acme.foodpackaging.persistence.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.record.DbJobRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_JOBS_db;

@ApplicationScoped
public class JobDBLoader {

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public Map<Integer, DbJobRow> loadJobRowMapFromDb(
            LocalDateTime from,
            LocalDateTime to,
            String ksk
    ) {

        List<DbJobRow> rows = (List<DbJobRow>) em
                .createNativeQuery(LOAD_JOBS_db, "DbJobRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .setParameter(3, ksk)
                .getResultList();

        return rows.stream()
                .collect(Collectors.toMap(
                        r -> r.snpz().intValueExact(),
                        r -> r,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ));
    }
}

