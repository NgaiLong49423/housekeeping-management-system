package com.housekeeping.management.dto.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.housekeeping.management.entity.BookingDelayCompensationTier;
import com.housekeeping.management.entity.FeedbackCase;
import com.housekeeping.management.entity.FeedbackCaseEvidence;
import com.housekeeping.management.entity.FinancialPayoutResponse;
import com.housekeeping.management.entity.StaffIncidentAction;
import com.housekeeping.management.entity.StaffIncidentActionEvidence;
import com.housekeeping.management.entity.StaffRecoveryInstallment;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NewPersistenceDtoConversionTest {
    @Test
    void delayTierMapsToAndFromJpaEntity() {
        BookingDelayCompensationTierDTO dto = new BookingDelayCompensationTierDTO(2, 16, 30, 10);
        assertThat(BookingDelayCompensationTierDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void feedbackCaseMapsToAndFromJpaEntity() {
        FeedbackCaseDTO dto = new FeedbackCaseDTO(3L, 4L, "VERIFIED");
        assertThat(FeedbackCaseDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void feedbackCaseMapsCustomerDeclaredValuationInBothDirections() {
        FeedbackCaseDTO dto = new FeedbackCaseDTO(3L,4L,"PROPERTY_DAMAGE","Mặt bàn trầy",
                "Bàn gỗ",new java.math.BigDecimal("750000"),30,"RECEIVED",null,"Mong công ty kiểm tra",null,false,
                null,null,"NOT_PROPOSED",null,null,null,null,LocalDateTime.of(2026,11,11,9,0),null);
        assertThat(FeedbackCaseDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void feedbackEvidenceMapsToAndFromJpaEntityIncludingMessageId() {
        FeedbackCaseEvidenceDTO dto = new FeedbackCaseEvidenceDTO(5L, 3L, "evidence.png", 6L,
                LocalDateTime.of(2026, 11, 11, 9, 30), 7L);
        FeedbackCaseEvidence entity = dto.convertDTOToEntity();
        assertThat(entity.getMessageId()).isEqualTo(7L);
        assertThat(FeedbackCaseEvidenceDTO.convertEntityToDTO(entity)).isEqualTo(dto);
    }

    @Test
    void payoutResponseConverterKeepsEncryptedPayloadInPersistenceDto() {
        FinancialPayoutResponseDTO dto = new FinancialPayoutResponseDTO(8L, 9L, 10L,
                "ACCEPTED", "BANK_TRANSFER", "ciphertext-only", LocalDateTime.of(2026, 11, 11, 10, 0),
                LocalDateTime.of(2026,11,12,11,0),LocalDateTime.of(2026,11,11,9,0),LocalDateTime.of(2026,11,11,9,5));
        FinancialPayoutResponse entity = dto.convertDTOToEntity();
        assertThat(entity.getEncryptedAccountDetails()).isEqualTo("ciphertext-only");
        assertThat(FinancialPayoutResponseDTO.convertEntityToDTO(entity)).isEqualTo(dto);
    }

    @Test
    void verificationConverterPreservesReviewedEvidenceAndInternalFinding() {
        FeedbackCaseVerificationDTO dto = new FeedbackCaseVerificationDTO(1L, 3L, 8L, "VERIFIED",
                "12,14", "Ảnh xác nhận vết xước.", LocalDateTime.of(2026, 11, 11, 10, 0));
        assertThat(FeedbackCaseVerificationDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void staffIncidentActionConverterPreservesExplanationDeadlineAndFinalDecision() {
        StaffIncidentAction entity=new StaffIncidentAction(); entity.setActionId(7L); entity.setCaseId(8L); entity.setStaffId(9L);
        entity.setActionType("PROPOSE_RECOVERY"); entity.setStatus("RECOVERY_DUE"); entity.setReason("Biên bản sửa chữa");
        entity.setProposedBy(10L); entity.setDecidedBy(11L); entity.setResponseDeadline(LocalDateTime.of(2026,11,12,10,0));
        entity.setStaffResponseExpiredAt(LocalDateTime.of(2026,11,12,10,0)); entity.setDecisionOutcome("RECOVERY");
        entity.setLiabilityAmount(new java.math.BigDecimal("90000")); entity.setFinalizedAt(LocalDateTime.of(2026,11,14,10,0));
        assertThat(StaffIncidentActionDTO.convertEntityToDTO(entity).convertDTOToEntity().getStaffResponseExpiredAt())
                .isEqualTo(entity.getStaffResponseExpiredAt());
        assertThat(StaffIncidentActionDTO.convertEntityToDTO(entity).convertDTOToEntity().getLiabilityAmount())
                .isEqualByComparingTo("90000");
    }

    @Test
    void staffActionEvidenceConverterPreservesPrivateUploaderAndStage() {
        StaffIncidentActionEvidenceDTO dto=new StaffIncidentActionEvidenceDTO(1L,2L,3L,"APPEAL","PDF","application/pdf",
                "invoice.pdf","stored-proof.pdf",LocalDateTime.of(2026,11,11,15,0));
        assertThat(StaffIncidentActionEvidenceDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }

    @Test
    void staffRecoveryInstallmentConverterPreservesReceiptAndActualPayment() {
        StaffRecoveryInstallmentDTO dto=new StaffRecoveryInstallmentDTO(1L,2L,new java.math.BigDecimal("25000"),
                "CASH","RECEIPT-1","receipt.png",3L,LocalDateTime.of(2026,11,11,16,0));
        assertThat(StaffRecoveryInstallmentDTO.convertEntityToDTO(dto.convertDTOToEntity())).isEqualTo(dto);
    }
}
