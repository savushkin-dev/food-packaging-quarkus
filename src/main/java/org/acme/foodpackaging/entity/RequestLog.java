package org.acme.foodpackaging.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "PLR_USERLOG")
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "F_ID")
    private Long id;

    @Column(name = "LOGIN", columnDefinition = "CHAR(20)")
    private String login;

    @Column(name = "DT")
    private LocalDateTime dateTime;

    @Column(name = "IP", columnDefinition = "CHAR(15)")
    private String ip;

    @Column(name = "METHOD", columnDefinition = "CHAR(15)")
    private String method;

    @Column(name = "QUERY", columnDefinition = "CHAR(7000)")
    private String query;
}
