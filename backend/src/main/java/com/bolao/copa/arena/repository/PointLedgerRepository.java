package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface PointLedgerRepository extends JpaRepository<PointLedgerEntry, Long> {
    Optional<PointLedgerEntry> findByIdempotencyKey(String key);
    List<PointLedgerEntry> findTop100ByWalletOrderByCreatedAtDesc(PointWallet wallet);
    @Override
    @EntityGraph(attributePaths = {"wallet", "wallet.user"})
    Page<PointLedgerEntry> findAll(Pageable pageable);
    @EntityGraph(attributePaths = {"wallet", "wallet.user"})
    @Query("select entry from PointLedgerEntry entry join entry.wallet wallet join wallet.user user " +
            "where lower(entry.description) like lower(concat('%', :search, '%')) " +
            "or lower(user.name) like lower(concat('%', :search, '%')) " +
            "or lower(user.email) like lower(concat('%', :search, '%')) " +
            "or lower(entry.referenceType) like lower(concat('%', :search, '%'))")
    Page<PointLedgerEntry> search(String search, Pageable pageable);
}
