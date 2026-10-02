package org.acme.foodpackaging.dto.materials;

import java.time.LocalDate;
import java.util.List;

/**
 * Контекст сохранения лога отправки в 1С.
 * Группирует связанные параметры, чтобы уменьшить количество аргументов метода.
 */
public record OneCReqLogContext(
        LocalDate dt,
        String kpp1,
        String kpp2,
        String type,
        List<ProductWithMaterialsDto> data,
        String req1c,
        String userId,
        String userFio,
        String ip
) {}