package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;
import org.acme.foodpackaging.domain.Job;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Getter
@Setter
public class MsLogInsertRow {
  private String idBatch;
  private String productId;
  private String lineIdFact;
  private Integer np;
  private Integer eventType;
  private LocalDateTime dtv;
  private LocalDateTime eventTime;

  public MsLogInsertRow(Job job, int eventType, LocalDateTime eventTime){
      this.idBatch =  job.getIdBatch();
      this.productId =  job.getProduct().getId();
      this.lineIdFact = job.getLineIdFact();
      this.np = job.getNp();
      this.eventType = eventType;
      this.dtv = job.getDtv();
      this.eventTime = eventTime;
  }
}