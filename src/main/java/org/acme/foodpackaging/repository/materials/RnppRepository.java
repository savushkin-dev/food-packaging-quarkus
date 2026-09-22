package org.acme.foodpackaging.repository.materials;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.acme.foodpackaging.entity.materials.PlrRnpp;
import org.acme.foodpackaging.sql.SqlQueries;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RnppRepository implements PanacheRepository<PlrRnpp> {


    private final EntityManager em;
    private final SqlQueries sqlQueries;

    @Inject
    public RnppRepository(EntityManager em, SqlQueries sqlQueries) {
        this.em = em;
        this.sqlQueries = sqlQueries;
    }

    @SuppressWarnings("unchecked")
    public List<PlrRnpp> findByKmcAndKtAndEmkAndSysn(Double sysn, String kmc, String kt, Double emk) {
        String sql = sqlQueries.loadRnppGroupedByKkom();

        Query query = em.createNativeQuery(sql);
        query.setParameter(1, sysn);
        query.setParameter(2, kmc);
        query.setParameter(3, kt);
        query.setParameter(4, emk);

        List<Object[]> results = query.getResultList();

        List<PlrRnpp> result = new ArrayList<>();
        for (Object[] row : results) {
            PlrRnpp entity = new PlrRnpp();
            entity.setSysn(((Double) row[0]));
            entity.setKmc((String) row[1]);
            entity.setKt((String) row[2]);
            entity.setEmk(((Double) row[3]));
            entity.setKkom((String) row[4]);
            entity.setKol1t((Double) row[5]);
            entity.setKolvk(((Double) row[6]));
            result.add(entity);
        }

        return result;
    }
}