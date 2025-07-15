package org.acme.foodpackaging.domain;

import java.time.Duration;

// Класс для расчета времени выполнения партии
public class DurationProvider {

    private static final int IF_CHANGING_PACKAGING = 4; // Время, которое может потребоваться для смены упаковки
    private static final int SPEED_PLUSH_ON_LINE_1 = 164;
    private static final int SPEED_CACTUS_ON_LINE_1_2_3 = 184; //
    private static final int SPEED_ROD_ON_LINE_4_5_6 = 198;
    private static final int SPEED_CLASSIC_ON_LINE_1_2_3 = 200;
    private static final int SPEED_CLASSIC_ON_LINE_6 = 240;
    private static final int SPEED_DEFAULT = 200;

    public Duration calculate(Product product, Line line, int quantity) { // Метод для динамического расчета времени производства партии
        int speed;
        switch (product.getType()) {
            case PLUSH:
                speed = SPEED_PLUSH_ON_LINE_1;
                break;
            case CACTUS:
                speed = SPEED_CACTUS_ON_LINE_1_2_3;
                break;
            case ROD:
                speed = SPEED_ROD_ON_LINE_4_5_6;
                break;
            case CLASSIC:
                if (line != null && "6".equals(line.getId())) {
                    speed = SPEED_CLASSIC_ON_LINE_6;
                    return Duration.ofMinutes((long)Math.ceil(quantity / (double)speed) + IF_CHANGING_PACKAGING);
                } else {
                    speed = SPEED_CLASSIC_ON_LINE_1_2_3;
                }
                break;
            default:
                speed = SPEED_DEFAULT;
        }
        return Duration.ofMinutes((long)Math.ceil(quantity / (double)speed) + IF_CHANGING_PACKAGING);
    }
    public Duration calculate(Product product, int quantity){ // Для типов сырков у которых скорость на всех линиях одинакова
        int speed = switch (product.getType()) {             // и может быть рассчитана при инициализации списка Jobs
            case PLUSH -> SPEED_PLUSH_ON_LINE_1;
            case CACTUS -> SPEED_CACTUS_ON_LINE_1_2_3;
            case ROD -> SPEED_ROD_ON_LINE_4_5_6;
            default -> SPEED_DEFAULT;
        };
        return Duration.ofMinutes((long)Math.ceil(quantity / (double)speed) + IF_CHANGING_PACKAGING);
    }
}
