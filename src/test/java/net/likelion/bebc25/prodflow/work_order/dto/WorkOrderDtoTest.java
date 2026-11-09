package net.likelion.bebc25.prodflow.work_order.dto;

import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;

class WorkOrderDtoTest {
    @Test
    @DisplayName("담당자가 미배정된 작업지시도 목록 응답으로 변환한다")
    void pageResponseWithoutAssignedUser() {
        WorkOrder workOrder = mock(WorkOrder.class, RETURNS_DEEP_STUBS);
        when(workOrder.getUser()).thenReturn(null);

        WorkOrderDto.PageResponse response = WorkOrderDto.PageResponse.from(workOrder);

        assertThat(response.assignedName()).isNull();
    }

    @Test
    @DisplayName("미배정 작업지시의 상세도 담당자 ID와 이름을 null로 반환한다")
    void detailResponseWithoutAssignedUser() {
        WorkOrder workOrder = mock(WorkOrder.class, RETURNS_DEEP_STUBS);
        when(workOrder.getUser()).thenReturn(null);

        WorkOrderDto.DetailResponse response = WorkOrderDto.DetailResponse.from(workOrder, List.of());

        assertThat(response.assignedId()).isNull();
        assertThat(response.assignedName()).isNull();
    }
}
