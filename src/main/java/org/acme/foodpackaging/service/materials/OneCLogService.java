package org.acme.foodpackaging.service.materials;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.dto.materials.OneCReqGroupDto;
import org.acme.foodpackaging.entity.materials.Plr1cReq;
import org.acme.foodpackaging.repository.materials.OneCReqRepository;
import org.acme.foodpackaging.service.materials.config.MtService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class OneCLogService {

    private final OneCReqRepository oneCReqRepository;
    private final MtService mtService;

    @Inject
    public OneCLogService(OneCReqRepository oneCReqRepository, MtService mtService) {
        this.oneCReqRepository = oneCReqRepository;
        this.mtService = mtService;
    }

    /**
     * Получает лог отправок в 1С по дате, МОЛ и типу, сгруппированный по REQ1C
     */
    public List<OneCReqGroupDto> getLog(String date, String kpp, String type) {
        LocalDate dt = LocalDate.parse(date);

        List<Plr1cReq> records = oneCReqRepository.findByDtAndKpp2AndType(dt, kpp, type);

        Map<String, List<Plr1cReq>> groupedByReq1c = records.stream()
                .filter(r -> r.getReq1c() != null && !r.getReq1c().isEmpty())
                .collect(Collectors.groupingBy(
                        Plr1cReq::getReq1c,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<OneCReqGroupDto> result = new ArrayList<>();
        for (Map.Entry<String, List<Plr1cReq>> entry : groupedByReq1c.entrySet()) {
            List<Plr1cReq> group = entry.getValue();
            Plr1cReq first = group.getFirst();

            List<OneCReqGroupDto.OneCReqItemDto> materials = group.stream()
                    .map(r -> OneCReqGroupDto.OneCReqItemDto.builder()
                            .kmt(r.getKmt())
                            .snmMt(mtService.getByKmt(r.getKmt()).getSnm())
                            .eduMt(mtService.getByKmt(r.getKmt()).getEdu())
                            .kole(r.getKole())
                            .build())
                    .collect(Collectors.toList());

            result.add(OneCReqGroupDto.builder()
                    .req1c(entry.getKey())
                    .kpp1(first.getKpp1().trim())
                    .kpp2(first.getKpp2().trim())
                    .type(first.getType())
                    .userId(first.getUserId())
                    .ip(first.getIp())
                    .sentAt(first.getSentAt())
                    .materials(materials)
                    .build());
        }

        return result;
    }
}