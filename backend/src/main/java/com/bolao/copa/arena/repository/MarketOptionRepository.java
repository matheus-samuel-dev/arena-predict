package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MarketOptionRepository extends JpaRepository<MarketOption, Long> {
    List<MarketOption> findByMarketOrderByIdAsc(PredictionMarket market);
    Optional<MarketOption> findByIdAndMarket(Long id, PredictionMarket market);
    Optional<MarketOption> findByMarketAndKey(PredictionMarket market, String key);
    @Query("select option from MarketOption option join fetch option.market " +
            "where option.market in :markets order by option.market.id, option.id")
    List<MarketOption> findForMarkets(@Param("markets") Collection<PredictionMarket> markets);
}
