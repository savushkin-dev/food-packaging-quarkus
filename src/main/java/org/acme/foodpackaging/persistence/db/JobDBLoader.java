package org.acme.foodpackaging.persistence.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.DbMaintenanceRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.sql.SqlQueries.*;

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
                .createNativeQuery(LOAD_JOBS_DB, "DbJobRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .setParameter(3, ksk)
                .setParameter(4, Timestamp.valueOf(from))
                .getResultList();

        return rows.stream()
                .collect(Collectors.toMap(
                        r -> r.snpz().intValueExact(),
                        r -> r,
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate SNPZ: " + existing.snpz()
                            );
                        }
                ));
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, DbMaintenanceRow> loadMaintenanceRowMapFromDb(
            LocalDateTime from,
            LocalDateTime to
    ) {

        List<DbMaintenanceRow> rows = (List<DbMaintenanceRow>) em
                .createNativeQuery(LOAD_MAINTENANCE_DB, "DbMaintenanceRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .setParameter(3, Timestamp.valueOf(from))
                .setParameter(4, Timestamp.valueOf(to))
                .getResultList();

        return rows.stream()
                .collect(Collectors.toMap(
                        DbMaintenanceRow::f_id,
                        r -> r,
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate F_ID: " + existing.f_id()
                            );
                        }
                ));
    }
}

