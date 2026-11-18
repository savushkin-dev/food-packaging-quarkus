package org.acme.foodpackaging.dto.materials;

import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.entity.materials.PlrSinv;

import java.time.LocalDate;
import java.util.Map;

public record LoadContext(
        Double sysn,
        LocalDate dt,
        String kpp,
        String type,
        Map<String, PlrSinv> existingDataMap,
        Map<String, PlrMt> mtCache,
        Map<String, Double> preliminaryKolf,
        Map<String, Double> preliminaryOrders
) {}