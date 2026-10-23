package com.example.settlement.service.impl;

import com.example.settlement.audit.AuditActions;
import com.example.settlement.audit.AuditService;
import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.TradeCreateRequest;
import com.example.settlement.dto.request.TradeUpdateRequest;
import com.example.settlement.dto.response.TradeResponse;
import com.example.settlement.entity.Trade;
import com.example.settlement.enums.TradeStatus;
import com.example.settlement.enums.TradeType;
import com.example.settlement.exception.BusinessRuleException;
import com.example.settlement.exception.DuplicateResourceException;
import com.example.settlement.exception.InvalidStateTransitionException;
import com.example.settlement.exception.ResourceNotFoundException;
import com.example.settlement.mapper.TradeMapper;
import com.example.settlement.repository.TradeRepository;
import com.example.settlement.service.TradeService;
import com.example.settlement.util.ReferenceGenerator;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TradeServiceImpl implements TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeServiceImpl.class);
    private static final int REFERENCE_ATTEMPTS = 5;
    private static final String ENTITY = "TRADE";

    private final TradeRepository tradeRepository;
    private final AuditService auditService;

    public TradeServiceImpl(TradeRepository tradeRepository, AuditService auditService) {
        this.tradeRepository = tradeRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public TradeResponse create(TradeCreateRequest request) {
        validateDates(request.tradeDate(), request.deliveryDate());

        String reference = StringUtils.hasText(request.tradeReference())
                ? request.tradeReference().trim().toUpperCase(Locale.ROOT)
                : generateUniqueReference();

        if (tradeRepository.existsByTradeReference(reference)) {
            throw DuplicateResourceException.of("tradeReference", reference);
        }

        Trade trade = Trade.builder()
                .tradeReference(reference)
                .commodity(request.commodity().trim())
                .tradeType(request.tradeType())
                .buyerName(request.buyerName().trim())
                .sellerName(request.sellerName().trim())
                .quantity(requirePositive(request.quantity(), "quantity"))
                .unitOfMeasure(request.unitOfMeasure().trim().toUpperCase(Locale.ROOT))
                .unitPrice(requirePositive(request.unitPrice(), "unitPrice"))
                .currencyCode(normaliseCurrency(request.currencyCode()))
                .tradeDate(request.tradeDate())
                .deliveryDate(request.deliveryDate() != null ? request.deliveryDate() : request.tradeDate())
                .status(request.status() != null ? request.status() : TradeStatus.DRAFT)
                .notes(request.notes())
                .build();

        Trade saved = tradeRepository.save(trade);
        TradeResponse response = TradeMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(), AuditActions.TRADE_CREATED, null, response,
                "Trade " + saved.getTradeReference() + " created");
        log.info("Created trade {} (id={})", saved.getTradeReference(), saved.getId());
        return response;
    }

    @Override
    @Transactional
    public TradeResponse update(Long id, TradeUpdateRequest request) {
        Trade trade = requireTrade(id);
        TradeResponse before = TradeMapper.toResponse(trade);

        if (trade.getStatus() != null && trade.getStatus().isTerminal()) {
            throw new BusinessRuleException("TRADE_NOT_MODIFIABLE",
                    "Trade " + trade.getTradeReference() + " is " + trade.getStatus() + " and can no longer be modified");
        }

        LocalDate newTradeDate = request.tradeDate() != null ? request.tradeDate() : trade.getTradeDate();
        LocalDate newDeliveryDate = request.deliveryDate() != null ? request.deliveryDate() : trade.getDeliveryDate();
        validateDates(newTradeDate, newDeliveryDate);

        if (request.commodity() != null) {
            trade.setCommodity(request.commodity().trim());
        }
        if (request.tradeType() != null) {
            trade.setTradeType(request.tradeType());
        }
        if (request.buyerName() != null) {
            trade.setBuyerName(request.buyerName().trim());
        }
        if (request.sellerName() != null) {
            trade.setSellerName(request.sellerName().trim());
        }
        if (request.quantity() != null) {
            trade.setQuantity(requirePositive(request.quantity(), "quantity"));
        }
        if (request.unitOfMeasure() != null) {
            trade.setUnitOfMeasure(request.unitOfMeasure().trim().toUpperCase(Locale.ROOT));
        }
        if (request.unitPrice() != null) {
            trade.setUnitPrice(requirePositive(request.unitPrice(), "unitPrice"));
        }
        if (request.currencyCode() != null) {
            trade.setCurrencyCode(normaliseCurrency(request.currencyCode()));
        }
        trade.setTradeDate(newTradeDate);
        trade.setDeliveryDate(newDeliveryDate);
        if (request.notes() != null) {
            trade.setNotes(request.notes());
        }

        boolean statusChanged = false;
        if (request.status() != null && request.status() != trade.getStatus()) {
            TradeStatus current = trade.getStatus();
            if (!current.canTransitionTo(request.status())) {
                throw new InvalidStateTransitionException("trade", current, request.status());
            }
            trade.setStatus(request.status());
            statusChanged = true;
        }

        Trade saved = tradeRepository.save(trade);
        TradeResponse after = TradeMapper.toResponse(saved);
        auditService.record(ENTITY, saved.getId(),
                statusChanged ? AuditActions.TRADE_STATUS_CHANGED : AuditActions.TRADE_UPDATED,
                before, after, "Trade " + saved.getTradeReference() + " updated");
        log.info("Updated trade {} (id={})", saved.getTradeReference(), saved.getId());
        return after;
    }

    @Override
    @Transactional(readOnly = true)
    public TradeResponse getById(Long id) {
        return TradeMapper.toResponse(requireTrade(id));
    }

    @Override
    @Transactional(readOnly = true)
    public TradeResponse getByReference(String tradeReference) {
        Trade trade = tradeRepository.findByTradeReference(tradeReference.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Trade", tradeReference));
        return TradeMapper.toResponse(trade);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TradeResponse> search(String commodity, TradeStatus status, TradeType tradeType,
                                              LocalDate tradeDateFrom, LocalDate tradeDateTo, Pageable pageable) {
        Specification<Trade> specification = buildSpecification(commodity, status, tradeType, tradeDateFrom, tradeDateTo);
        Page<Trade> page = tradeRepository.findAll(specification, pageable);
        return PageResponse.from(page, TradeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Trade requireTrade(Long id) {
        return tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade", id));
    }

    private Specification<Trade> buildSpecification(String commodity, TradeStatus status, TradeType tradeType,
                                                    LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(commodity)) {
                predicates.add(cb.like(cb.lower(root.get("commodity")),
                        "%" + commodity.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (tradeType != null) {
                predicates.add(cb.equal(root.get("tradeType"), tradeType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("tradeDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("tradeDate"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            String candidate = ReferenceGenerator.trade();
            if (!tradeRepository.existsByTradeReference(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("REFERENCE_GENERATION_FAILED",
                "Unable to generate a unique trade reference, please retry");
    }

    private void validateDates(LocalDate tradeDate, LocalDate deliveryDate) {
        if (tradeDate != null && deliveryDate != null && deliveryDate.isBefore(tradeDate)) {
            throw new BusinessRuleException("INVALID_DELIVERY_DATE",
                    "deliveryDate (" + deliveryDate + ") must not be earlier than tradeDate (" + tradeDate + ")");
        }
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException("INVALID_" + field.toUpperCase(Locale.ROOT),
                    field + " must be greater than zero");
        }
        return value;
    }

    private String normaliseCurrency(String currencyCode) {
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }
}
