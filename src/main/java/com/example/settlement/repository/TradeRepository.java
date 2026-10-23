package com.example.settlement.repository;

import com.example.settlement.entity.Trade;
import com.example.settlement.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    Optional<Trade> findByTradeReference(String tradeReference);

    boolean existsByTradeReference(String tradeReference);

    boolean existsByTradeReferenceAndIdNot(String tradeReference, Long id);

    List<Trade> findByCommodityIgnoreCase(String commodity);

    long countByStatus(TradeStatus status);
}
