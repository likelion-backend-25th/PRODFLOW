package net.likelion.bebc25.prodflow.work_order.dto;

import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrderHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkOrderHistoryDtoTest {

    @ParameterizedTest
    @CsvSource({"false, true", "true, false", "false, false", "true, true"})
    @DisplayName("변경 전후 담당자가 미배정이어도 이력을 변환하고 나머지 정보를 유지한다")
    void responseWithOptionalAssignees(boolean hasPreviousUser, boolean hasNewUser) {
        WorkOrderHistory history = mock(WorkOrderHistory.class, RETURNS_DEEP_STUBS);
        User previousUser = mock(User.class);
        User newUser = mock(User.class);
        if (hasPreviousUser) {
            when(previousUser.getName()).thenReturn("이전 담당자");
        }
        if (hasNewUser) {
            when(newUser.getName()).thenReturn("새 담당자");
        }
        when(history.getPreviousUser()).thenReturn(hasPreviousUser ? previousUser : null);
        when(history.getNewUser()).thenReturn(hasNewUser ? newUser : null);
        when(history.getPreviousLine().getLineName()).thenReturn("A라인");
        when(history.getNewLine().getLineName()).thenReturn("B라인");
        when(history.getChangedBy().getName()).thenReturn("변경 직원");

        WorkOrderHistoryDto.Response response = WorkOrderHistoryDto.Response.from(history);

        assertThat(response.previousUserName()).isEqualTo(hasPreviousUser ? "이전 담당자" : null);
        assertThat(response.newUserName()).isEqualTo(hasNewUser ? "새 담당자" : null);
        assertThat(response.previousLineName()).isEqualTo("A라인");
        assertThat(response.newLineName()).isEqualTo("B라인");
        assertThat(response.changedByName()).isEqualTo("변경 직원");
    }
}
