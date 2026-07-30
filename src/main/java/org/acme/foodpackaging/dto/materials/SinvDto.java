package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinvDto {
    private LocalDate dt;
    private String kpp;
    private String kmc;
    private String kt;
    private String kmt;
    private String snmMt;        // Название материала
    private String eduMt;        // Единица измерения
    private Double norm;         // Норма на тонну
    private Double normf;        // Норма на заказ (для ЭТОГО продукта)
    private Double totalNormf;   // Норма по ВСЕМ продуктам
    private Double kolf;         // Остаток (общий)
    private Double insurancePerc;// % страховки
    private Double roundStep;    // Шаг округления
    private Double trnd;         // Норма со страховкой и округлением
    private Double order;        // Итоговый дозаказ
    private Integer productCount;// Количество продуктов с этим материалом
}