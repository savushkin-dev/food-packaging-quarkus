package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCReqGroupDto {
    private String req1c;          // Номер заявки в 1С
    private String kpp1;           // Склад
    private String kpp2;           // МОЛ
    private String type;           // M/P
    private String userId;         // Табельный
    private String userFio;        // ФИО отправителя
    private String ip;             // IP
    private LocalDateTime sentAt;  // Дата/время отправки
    private List<OneCReqItemDto> materials;  // Список материалов

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OneCReqItemDto {
        private String kmt;    // Код материала
        private String snmMt;  // Название материала
        private String eduMt;  // Единица измерения
        private Double kole;   // Количество
    }
}