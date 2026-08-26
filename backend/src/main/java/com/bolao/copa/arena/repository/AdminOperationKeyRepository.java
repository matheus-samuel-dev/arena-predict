package com.bolao.copa.arena.repository;

import com.bolao.copa.arena.domain.AdminOperationKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminOperationKeyRepository extends JpaRepository<AdminOperationKey, Long> {
    Optional<AdminOperationKey> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}
