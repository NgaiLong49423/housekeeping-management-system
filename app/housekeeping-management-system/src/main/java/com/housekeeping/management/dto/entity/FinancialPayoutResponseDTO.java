package com.housekeeping.management.dto.entity;

import com.housekeeping.management.entity.FinancialPayoutResponse;
import java.time.LocalDateTime;

/** Persistence converter only. Encrypted account data must never be returned from a web endpoint. */
public record FinancialPayoutResponseDTO(Long payoutResponseId, Long entryId, Long customerId,
        String responseStatus, String payoutMethod, String encryptedAccountDetails, LocalDateTime respondedAt,
        LocalDateTime customerConfirmedAt, LocalDateTime amountAgreedAt, LocalDateTime payoutMethodSelectedAt) {
    public FinancialPayoutResponseDTO(Long payoutResponseId, Long entryId, Long customerId,
            String responseStatus, String payoutMethod, String encryptedAccountDetails, LocalDateTime respondedAt) {
        this(payoutResponseId,entryId,customerId,responseStatus,payoutMethod,encryptedAccountDetails,respondedAt,null,null,null);
    }

    public static FinancialPayoutResponseDTO convertEntityToDTO(FinancialPayoutResponse entity) {
        if (entity == null) return null;
        return new FinancialPayoutResponseDTO(entity.getPayoutResponseId(), entity.getEntryId(), entity.getCustomerId(),
                entity.getResponseStatus(), entity.getPayoutMethod(), entity.getEncryptedAccountDetails(), entity.getRespondedAt(),
                entity.getCustomerConfirmedAt(),entity.getAmountAgreedAt(),entity.getPayoutMethodSelectedAt());
    }

    public FinancialPayoutResponse convertDTOToEntity() {
        FinancialPayoutResponse entity = new FinancialPayoutResponse();
        entity.setPayoutResponseId(payoutResponseId);
        entity.setEntryId(entryId);
        entity.setCustomerId(customerId);
        entity.setResponseStatus(responseStatus);
        entity.setPayoutMethod(payoutMethod);
        entity.setEncryptedAccountDetails(encryptedAccountDetails);
        entity.setRespondedAt(respondedAt);
        entity.setCustomerConfirmedAt(customerConfirmedAt);
        entity.setAmountAgreedAt(amountAgreedAt);
        entity.setPayoutMethodSelectedAt(payoutMethodSelectedAt);
        return entity;
    }
}
