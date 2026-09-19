package com.sssok.application.port.out;

import com.sssok.domain.admin.Admin;
import com.sssok.domain.admin.AdminRole;
import java.util.List;
import java.util.Optional;

public interface AdminRepository {

    Admin save(Admin admin);

    Optional<Admin> findById(Long id);

    Optional<Admin> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<Admin> findAllOrderByIdAsc();

    // 마지막 슈퍼관리자를 지우거나 강등하는 걸 막을 때 쓴다.
    long countByRole(AdminRole role);

    void deleteById(Long id);
}
