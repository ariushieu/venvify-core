package com.venvify.venvifycore.user.repository;

import com.venvify.venvifycore.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(String publicId);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);

    boolean existsByHostHandle(String hostHandle);

    /** Storefront public theo vanity handle (plan P3 §2.4). */
    Optional<User> findByHostHandleAndDeletedFalse(String hostHandle);
}
