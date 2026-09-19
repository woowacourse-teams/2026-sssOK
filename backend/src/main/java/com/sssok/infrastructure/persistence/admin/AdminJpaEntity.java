package com.sssok.infrastructure.persistence.admin;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// member 테이블에 얹지 않고 따로 둔다.
@Entity
@Table(
    name = "admin",
    uniqueConstraints = @UniqueConstraint(name = "uk_admin_login_id", columnNames = "login_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 20)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    public AdminJpaEntity(
        Long id, String loginId, String passwordHash, String name, String role, Instant createdAt
    ) {
        super(createdAt);
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }
}
