package org.acme.foodpackaging.entity.materials;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Лог отправки заявок в 1С
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "PLR_1CREQ", schema = "dbo")
public class Plr1cReq extends PanacheEntityBase {

    /** Уникальный идентификатор записи (PK) */
    @Id
    @UuidGenerator
    @Column(name = "F_GUID", columnDefinition = "uniqueidentifier")
    public UUID fGuid;

    /** Идентификатор записи (автоинкремент) */
    @Column(name = "F_ID", nullable = false, insertable = false, updatable = false)
    public Long fId;

    /** Штамп времени (версия записи) */
    @Column(name = "F_TM", nullable = false, insertable = false, updatable = false)
    public byte[] fTm;

    /** Признак удаления (0 - активен, 1 - удален) */
    @Column(name = "F_DEL", nullable = false, insertable = false, updatable = false)
    public Integer fDel = 0;

    /** Дата заявки (не отправки) */
    @Column(name = "DT", nullable = false)
    public LocalDate dt;

    /** Код склада (отправитель, KPP1) */
    @Column(name = "KPP1", length = 10, nullable = false)
    public String kpp1 = "";

    /** Код МОЛ (получатель, KPP2) */
    @Column(name = "KPP2", length = 10, nullable = false)
    public String kpp2 = "";

    /** Тип заявки: M - основная, P - предварительная */
    @Column(name = "TYPE", length = 1, nullable = false)
    public String type = "M";

    /** Код материала */
    @Column(name = "KMT", length = 10, nullable = false)
    public String kmt = "";

    /** Количество материала */
    @Column(name = "KOLE", nullable = false)
    public Double kole = 0.0;

    /** Номер заявки, полученный из 1С (tasknumber) */
    @Column(name = "REQ1C", length = 20)
    public String req1c;

    /** Табельный номер сотрудника, отправившего заявку */
    @Column(name = "USER_ID", length = 20)
    public String userId;

    /** IP-адрес клиента */
    @Column(name = "IP", length = 45)
    public String ip;

    /** Дата и время отправки заявки в 1С */
    @Column(name = "SENT_AT", nullable = false)
    public LocalDateTime sentAt;
}