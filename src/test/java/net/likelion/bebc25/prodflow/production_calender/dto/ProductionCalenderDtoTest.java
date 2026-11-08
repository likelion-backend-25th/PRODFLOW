package net.likelion.bebc25.prodflow.production_calender.dto;

import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.line.entity.Line;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionCalenderDtoTest {

    @Test
    @DisplayName("작업지시 정보를 생산 캘린더 응답 DTO로 변환한다")
    void convertWorkOrderToResponse() {
        // given
        WorkOrder workOrder = mock(WorkOrder.class);
        Line line = mock(Line.class);
        CommonCode commonCode = mock(CommonCode.class);
        OrderProduct orderProduct = mock(OrderProduct.class);
        CustomerOrder customerOrder = mock(CustomerOrder.class);

        LocalDate startDate = LocalDate.of(2026, 11, 10);
        LocalDate endDate = LocalDate.of(2026, 11, 12);

        when(workOrder.getLine()).thenReturn(line);
        when(line.getId()).thenReturn(4L);
        when(line.getLineName()).thenReturn("4라인");

        when(workOrder.getId()).thenReturn(11L);
        when(workOrder.getWorkNo()).thenReturn("WO-202611-011");
        when(workOrder.getPlannedStartDate()).thenReturn(startDate);
        when(workOrder.getPlannedEndDate()).thenReturn(endDate);

        when(workOrder.getCommonCode()).thenReturn(commonCode);
        when(commonCode.getCode()).thenReturn("PRODUCE");
        when(workOrder.getOrderProduct()).thenReturn(orderProduct);
        when(orderProduct.getCustomerOrder()).thenReturn(customerOrder);
        when(customerOrder.getOrderNo()).thenReturn("PF-2026-001");
        when(orderProduct.getProductName()).thenReturn("디자인 A 투명 케이스");
        when(workOrder.getQuantity()).thenReturn(2000);

        // when
        ProductionCalenderDto.Response response =
                ProductionCalenderDto.Response.from(workOrder);

        // then
        assertThat(response.lineId()).isEqualTo(4L);
        assertThat(response.lineName()).isEqualTo("4라인");
        assertThat(response.workOrderId()).isEqualTo(11L);
        assertThat(response.workNo()).isEqualTo("WO-202611-011");
        assertThat(response.plannedStartDate()).isEqualTo(startDate);
        assertThat(response.plannedEndDate()).isEqualTo(endDate);
        assertThat(response.status()).isEqualTo("PRODUCE");
        assertThat(response.orderNo()).isEqualTo("PF-2026-001");
        assertThat(response.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(response.quantity()).isEqualTo(2000);
    }
}
