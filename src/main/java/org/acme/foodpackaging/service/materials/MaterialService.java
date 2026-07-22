package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.dto.materials.ProductDto;
import org.acme.foodpackaging.dto.materials.ZinvDto;
import org.acme.foodpackaging.entity.materials.Pp;
import org.acme.foodpackaging.entity.materials.Sprog;
import org.acme.foodpackaging.repository.materials.MaterialRepository;
import org.acme.foodpackaging.repository.materials.PpRepository;
import org.acme.foodpackaging.repository.materials.SprogRepository;


import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class MaterialService {

    @Inject
    MaterialRepository materialRepository;

    @Inject
    SprogRepository sprogRepository;

    @Inject
    PpRepository ppRepository;

    @Inject
    ZinvService zinvService;

    public List<ProductDto> getProductsByDate(String date) {
        return materialRepository.findProductsByDate(date);
    }

    @Transactional
    public void loadProductsToZinv(String date, String kpp) {
        //Получаем  KPP (из справочника PLR_PP ) и дату

        //Получаем продукты из BD_VZPMC по дате
        List<ProductDto> products = materialRepository.findProductsByDate(date);

        //Сохраняем в PLR_ZINV
        List<ZinvDto> zinvList = products.stream()
                .map(p -> ZinvDto.builder()
                        .dt(LocalDate.parse(date))
                        .kpp(kpp)
                        .kmc(p.getKmc())
                        .ean13(p.getEan13())
                        .name(p.getProductName())
                        .sumMass(p.getSumMass())
                        .build())
                .collect(Collectors.toList());

        zinvService.saveAll(zinvList);

        //Поиск производственной программы
        Sprog sprog = getSprogByDate("2026-07-15");

        System.out.println(sprog);

    }

    public Sprog getSprogByDate(String date) {
        return sprogRepository.findByDate(LocalDate.parse(date));
    }

    public List<Pp> getRecipients(){
        return ppRepository.findAll().list();
    }
}