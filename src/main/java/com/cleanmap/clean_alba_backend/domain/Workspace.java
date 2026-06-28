package com.cleanmap.clean_alba_backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
    name = "workspaces",
    indexes = @Index(name = "idx_workspace_clean_score", columnList = "clean_score")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workspaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    private Integer cleanScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkspaceStatus status;

    @Column(name = "access_password")
    private String accessPassword;
}