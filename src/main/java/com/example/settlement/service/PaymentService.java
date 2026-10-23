package com.example.settlement.service;

import com.example.settlement.dto.common.PageResponse;
import com.example.settlement.dto.request.PaymentCreateRequest;
import com.example.settlement.dto.response.PaymentResponse;
import com.example.settlement.entity.Payment;
import com.example.settlement.enums.PaymentDirection;
import com.example.settlement.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Payment capture, completion, failure and reversal. */
public interface PaymentService {

    PaymentResponse create(PaymentCreateRequest request);

    PaymentResponse getById(Long id);

    PaymentResponse complete(Long id);

    PaymentResponse fail(Long id, String reason);

    PaymentResponse reverse(Long id, String reason);

    List<PaymentResponse> findByInvoiceId(Long invoiceId);

    PageResponse<PaymentResponse> search(Long invoiceId,
                                         String paymentReference,
                                         PaymentStatus status,
                                         PaymentDirection direction,
                                         String currencyCode,
                                         String counterparty,
                                         LocalDate paymentDateFrom,
                                         LocalDate paymentDateTo,
                                         Pageable pageable);

    /** Loads the entity or throws {@code ResourceNotFoundException}; for use by other services. */
    Payment requirePayment(Long id);
}
