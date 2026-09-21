package com.sssok.infrastructure.persistence.admin;

import com.sssok.application.port.out.AdminRepository;
import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminName;
import com.sssok.domain.admin.AdminRole;
import com.sssok.domain.admin.LoginId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminRepositoryAdapter implements AdminRepository {

    private final AdminJpaRepository jpaRepository;

    @Override
    public Admin save(Admin admin) {
        return toDomain(jpaRepository.save(toEntity(admin)));
    }

    @Override
    public Optional<Admin> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Admin> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId).map(this::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }

    @Override
    public List<Admin> findAllOrderByIdAsc() {
        return jpaRepository.findAllByOrderByIdAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRole(AdminRole role) {
        return jpaRepository.countByRole(role.name());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private AdminJpaEntity toEntity(Admin admin) {
        return new AdminJpaEntity(
            admin.getId(),
            admin.getLoginId().value(),
            admin.getPasswordHash(),
            admin.getName().value(),
            admin.getRole().name(),
            admin.getCreatedAt()
        );
    }

    private Admin toDomain(AdminJpaEntity entity) {
        return Admin.reconstruct(
            entity.getId(),
            new LoginId(entity.getLoginId()),
            entity.getPasswordHash(),
            new AdminName(entity.getName()),
            AdminRole.from(entity.getRole()),
            entity.getCreatedAt()
        );
    }
}
