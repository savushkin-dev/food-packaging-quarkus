package org.acme.foodpackaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DbMaintenanceRow {
   private Long fId;
   private Short fDel;
   private String lineId;
   private Timestamp startProductionDateTime;
   private Timestamp endDateTime;
   private Integer duration;
   private Long snpz;
   private Integer maintenanceTypeId;
   private String maintenanceNote;
}
