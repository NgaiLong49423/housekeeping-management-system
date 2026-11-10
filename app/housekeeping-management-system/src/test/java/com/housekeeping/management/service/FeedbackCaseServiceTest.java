package com.housekeeping.management.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.housekeeping.management.dto.request.PortalForms.IncidentReviewForm;
import com.housekeeping.management.repository.FeedbackCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class FeedbackCaseServiceTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    FeedbackCaseRepository cases=mock(FeedbackCaseRepository.class);
    AccountAuthorizationService authorization=mock(AccountAuthorizationService.class);
    FeedbackCaseService service=new com.housekeeping.management.service.impl.FeedbackCaseServiceImpl(jdbc,cases,authorization);

    @Test void rejectsEmptyOrUnsupportedIncidentType() {
        assertThatThrownBy(()->service.open(4L,7L,java.util.List.of("THEFT"),"Bị mất đồ"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc,cases);
    }

    @Test void onlyOwnerWithOpenCompletedFeedbackCanCreateCase() {
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(4L),eq(7L),eq(7L))).thenReturn(0);
        assertThatThrownBy(()->service.open(4L,7L,java.util.List.of("PROPERTY_DAMAGE","CLEANLINESS"),"Đồ bị vỡ và nhà chưa sạch"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cases);
    }

    @Test void verifiedCaseRequiresCustomerFacingOutcome() {
        assertThatThrownBy(()->service.review(11L,3L,new IncidentReviewForm("VERIFIED","COMPENSATION"," ","Staff làm rơi đồ",false,"PROPOSE_WARNING","Thông báo cảnh cáo")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kết quả xác minh");
        verify(cases,never()).updateReview(anyLong(),anyString(),any(),any(),any(),anyBoolean(),anyLong());
    }

    @Test void customerCanSendTextOrAttachmentButNotAnEmptyMessage() {
        assertThatThrownBy(()->service.addCustomerMessage(11L,4L,7L,"  ",false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nội dung hoặc");
        verify(cases,never()).createCustomerMessage(anyLong(),anyLong(),anyLong(),any());

        when(cases.createCustomerMessage(11L,4L,7L,"Bổ sung ảnh" )).thenReturn(21L);
        org.assertj.core.api.Assertions.assertThat(service.addCustomerMessage(11L,4L,7L,"Bổ sung ảnh",true)).isEqualTo(21L);
        verify(cases).createCustomerMessage(11L,4L,7L,"Bổ sung ảnh");
    }

    @Test void adminTextReplyCreatesConversationMessageAndLocksFeedback() {
        when(cases.createAdminMessage(11L,3L,"Chúng tôi đang xác minh.")).thenReturn(22L);
        org.assertj.core.api.Assertions.assertThat(service.addAdminMessage(11L,3L,"Chúng tôi đang xác minh.",false)).isEqualTo(22L);
        verify(authorization).requireActiveAdmin(3L);
        verify(cases).markFeedbackResponded(11L,"Chúng tôi đang xác minh.");
    }

    @Test void rejectsOversizedMessageAndDoesNotPersistIt() {
        assertThatThrownBy(()->service.addAdminMessage(11L,3L,"x".repeat(4001),false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4.000");
        verify(cases,never()).createAdminMessage(anyLong(),anyLong(),any());
    }
}
