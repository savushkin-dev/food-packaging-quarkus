package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.entity.jobs.PDayEntity;
import org.acme.foodpackaging.entity.jobs.VzPMCEntity;

import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class PDayRepository implements PanacheRepository<PDayEntity> {

    @Inject
    NS_McRepository nsMcRepository;

    @Transactional
    public Map<String, Map<String, Object>> loadPDay(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        String ksk = "0119030000";

        List<PDayEntity> existing = find("dti >= ?1 and dti < ?2 and ksk = ?3",
                startDate.atStartOfDay(),
                endDate.atStartOfDay(),
                ksk).list();

        for (PDayEntity p : existing) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SNM", nsMcRepository.findByKmc(p.kmc).map(e -> e.shortName).orElse(""));
            row.put("KMC", p.kmc);
            row.put("DTI", p.dti);
            row.put("DTF", p.dtf);
            row.put("NP", p.np);
            row.put("KOLEV", p.quantity);
            row.put("UX", p.priority);
            row.put("SNPZ", p.snpz);
            row.put("MASSA", p.mass);
            result.put(String.valueOf(p.snpz), row);
        }

        List<VzPMCEntity> vzList = VzPMCEntity.find("dti >= ?1 and dti < ?2 and ksk = ?3",
                startDate.atStartOfDay(),
                endDate.atStartOfDay(),
                ksk).list();

        for (VzPMCEntity vz : vzList) {
            String key = String.valueOf(vz.snpz);
            if (!result.containsKey(key)) {
                // Вставляет в PLR_PDAYNP
                PDayEntity pday = new PDayEntity();
                pday.id = UUID.randomUUID();
                pday.ksk = ksk;
                pday.kmc = vz.kmc;
                pday.dti = vz.dti;
                pday.dtf = null;
                pday.np = vz.np;
                pday.quantity = vz.quantity;
                pday.priority = vz.priority;
                pday.snpz = vz.snpz;
                pday.mass = vz.mass;
                persist(pday);

                // Добавляет в result
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("SNM", vz.shortName);
                row.put("KMC", vz.kmc);
                row.put("DTI", vz.dti);
                row.put("DTF", null);
                row.put("NP", vz.np);
                row.put("KOLEV", vz.quantity);
                row.put("UX", vz.priority);
                row.put("SNPZ", vz.snpz);
                row.put("MASSA", vz.mass);
                result.put(key, row);
            }
        }

        return result;
    }

    @Transactional
    public void updatePDay(Map<String, LocalDate> mapsnpz) {
        for (Map.Entry<String, LocalDate> entry : mapsnpz.entrySet()) {
            PDayEntity.find("snpz = ?1", Integer.valueOf(entry.getKey()))
                    .firstResultOptional()
                    .ifPresent(p -> p.dtf = entry.getValue().atStartOfDay());
        }
    }
}
