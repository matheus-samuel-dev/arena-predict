package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.PointWallet;
import com.bolao.copa.entity.User;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
    Optional<PointWallet> findByUser(User user);
    @EntityGraph(attributePaths = "user")
    List<PointWallet> findByUserIn(Collection<User> users);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from PointWallet w where w.user = :user")
    Optional<PointWallet> findByUserForUpdate(User user);
    @Override @EntityGraph(attributePaths = "user") List<PointWallet> findAll();
}
