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
    // ===== КЛЮЧЕВЫЕ ПОЛЯ (привязка к продукту) =====
    private LocalDate dt;
    private String kpp;
    private String kmc;          // Код продукта
    private String kt;           // Код упаковки
    private String kmt;          // Код материала

    // ===== ИНФОРМАЦИЯ О МАТЕРИАЛЕ =====
    private String snmMt;        // Название материала
    private String eduMt;          // Единица учета материала
    private String snmKt;        // Название упаковки

    // ===== РАЗНЫЕ ДЛЯ КАЖДОГО ПРОДУКТА =====
    private Double norm;         // Норма на тонну
    private Double normf;        // Норма для ЭТОГО продукта ((масса/1000)*norm)

    // ===== ОБЩИЕ ДЛЯ ВСЕХ ПРОДУКТОВ С ЭТИМ МАТЕРИАЛОМ =====
    private Double totalNormf;   // Сумма normf по всем продуктам
    private Double kolf;         // Общий остаток (вводит мастер 1 раз)
    private Double insurancePerc;// % страховки (из PLR_MT)
    private Double roundStep;    // Шаг округления (из PLR_MT)
    private Double trnd;         // Норма со страховкой и округлением (roundup(totalNormf*(1+%)) / step) * step
    private Double order;        // Итоговый дозаказ (trnd - kolf)
    private Integer productCount;// Количество продуктов с этим материалом
}