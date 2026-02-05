package org.acme.foodpackaging.dto;

import lombok.Getter;
import lombok.Setter;
import org.acme.foodpackaging.domain.Job;

import java.sql.Timestamp;

@Getter
@Setter
public class MsLogInsertRow {
  private String idBatch;
  private String productId;
  private String lineIdFact;
  private Integer np;
  private Integer eventType;
  private Timestamp dtv;
  private Timestamp eventTime;

  public MsLogInsertRow(Job job, int eventType, Timestamp eventTime){
      this.idBatch =  job.getIdBatch();
      this.productId =  job.getProduct().getId();
      this.lineIdFact = job.getLineIdFact();
      this.np = job.getNp();
      this.eventType = eventType;
      this.dtv = Timestamp.valueOf(job.getDtv());
      this.eventTime = eventTime;
  }
}