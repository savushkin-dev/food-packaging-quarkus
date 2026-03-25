package org.acme.foodpackaging.entity.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PLR_PLAN", schema = "dbo")
@Getter
@Setter
public class PlrPlan {
    @Id
    @Column(name = "F_GUID", nullable = false)
    private UUID id;

    @Column(name = "DT")
    private LocalDateTime dti;

    @Column(name = "VERSION")
    private String version;

    @Column(name = "PLAN")
    private String solutionJson;
}
