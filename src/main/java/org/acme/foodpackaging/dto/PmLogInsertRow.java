package org.acme.foodpackaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PmLogInsertRow {
        private  String idBatch;
        private  String productId;
        private  LocalDateTime dtv;
        private  Integer np;
        private  Integer eventType;
        private  LocalDateTime eventTime;
        private  String lineId;
}
