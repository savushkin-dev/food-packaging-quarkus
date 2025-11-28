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

    @Column(name = "LOGIN")
    private String login;

    @Column(name = "DT")
    private LocalDateTime dateTime;

    @Column(name = "METHOD")
    private String method;

    @Column(name = "QUERY")
    private String query;
}
