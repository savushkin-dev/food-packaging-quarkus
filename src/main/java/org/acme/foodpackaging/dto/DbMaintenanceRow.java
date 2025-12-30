package org.acme.foodpackaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DbMaintenanceRow {
   private long fId;
   private short fDel;
   private String lineId;
   private Timestamp startProductionDateTime;
   private Timestamp endDateTime;
   private Integer duration;
   private long snpz;
   private String shortName;
}
