package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.TradeCreateRequest;
import com.example.settlement.dto.request.TradeUpdateRequest;
import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/** Business operations for commodity trades. */
public interface TradeService {

    TradeResponse create(TradeCreateRequest request);

    TradeResponse update(Long id, TradeUpdateRequest request);

    TradeResponse getById(Long id);

    TradeResponse getByReference(String tradeReference);

    PageResponse<TradeResponse> search(String commodity,
                                       TradeStatus status,
                                       TradeType tradeType,
                                       LocalDate tradeDateFrom,
                                       LocalDate tradeDateTo,
                                       Pageable pageable);

    /** Loads the entity or throws {@code ResourceNotFoundException}; for use by other services. */
    Trade requireTrade(Long id);
}
