package com.venvify.venvifycore.user.repository;

import com.venvify.venvifycore.user.entity.User;
import com.venvify.venvifycore.user.enums.Role;
import com.venvify.venvifycore.user.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(String publicId);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);

    boolean existsByHostHandle(String hostHandle);

    /** Storefront public theo vanity handle (plan P3 §2.4). */
    Optional<User> findByHostHandleAndDeletedFalse(String hostHandle);

    // ---- admin (P6 §4) ----

    /** Search CSKH theo email/tên + filter status; null = bỏ filter. */
    @Query("""
            select u from User u
            where u.deleted = false
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            order by u.id desc""")
    Page<User> adminSearch(@Param("q") String q, @Param("status") UserStatus status, Pageable pageable);

    long countByDeletedFalse();

    /** Số user mang role (KPI dashboard) — member of chạy trên bảng user_roles. */
    @Query("select count(u) from User u where u.deleted = false and :role member of u.roles")
    long countByRole(@Param("role") Role role);
}
