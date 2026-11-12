package net.likelion.bebc25.prodflow.quotation.service;

import net.likelion.bebc25.prodflow.commoncode.entity.CodeGroup;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.commoncode.enums.CodeStatus;
import net.likelion.bebc25.prodflow.customer_order.dto.CustomerOrderDto;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.enums.NotifyChannel;
import net.likelion.bebc25.prodflow.payment.dto.PaymentDto;
import net.likelion.bebc25.prodflow.payment.service.PaymentService;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationSendDto;
import net.likelion.bebc25.prodflow.quotation.entity.Quotation;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.enums.UserRole;
import net.likelion.bebc25.prodflow.work_order.dto.WorkOrderDto;
import net.likelion.bebc25.prodflow.work_order.entity.WorkOrder;
import net.likelion.bebc25.prodflow.work_order.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class QuotationSameDayStartIntegrationTest extends QuotationIntegrationTestSupport {
    @Autowired PaymentService paymentService;
    @Autowired WorkOrderService workOrderService;

    @BeforeEach
    void prepareSameDayOrder() {
        // 실제 오늘을 사용하는 서비스이므로 휴일의 불가 조건은 고정 날짜 단위 테스트에서 검증한다.
        assumeFalse(today().getDayOfWeek() == DayOfWeek.SATURDAY || today().getDayOfWeek() == DayOfWeek.SUNDAY);
        approvedForOrder();
        tx().executeWithoutResult(status -> {
            CodeGroup orders = em.createQuery("select g from CodeGroup g where g.groupCode = 'ORDER_STATUS'",
                    CodeGroup.class).getSingleResult();
            for (String code : List.of("CONFIRMED", "IN_PRODUCTION", "PACKING")) {
                persistCode(orders, code);
            }
            CodeGroup works = instantiate(CodeGroup.class);
            ReflectionTestUtils.setField(works, "groupCode", "WORK_ORDER_STATUS");
            ReflectionTestUtils.setField(works, "groupName", "작업 상태");
            em.persist(works);
            for (String code : List.of("PREPARE", "PRODUCE", "PACK", "PACK_DONE")) {
                persistCode(works, code);
            }
            User worker = User.builder().name("생산 담당자").email("same-day-worker@example.com")
                    .phone("010-1111-2222").password("encoded").role(UserRole.PRODUCTION_MANAGER).build();
            worker.changePassword("encoded");
            em.persist(worker);
        });
    }

    @Test
    void savesExistingDatesAndStartsAfterDepositWithoutChangingAgreedSchedule() {
        QuotationDto.DetailResponse quote = sameDayQuotation(200);
        var order = orderService.create(orderRequest(quote, true), userId);
        assertThat(order.plannedStartDate()).isEqualTo(today());
        assertThat(order.depositDueDate()).isEqualTo(today());

        var deposit = paymentService.recordManualDeposit(
                new PaymentDto.ManualDepositRequest(order.id(), order.totalAmount() / 2, null), userId);
        assertThat(deposit.workOrderIds()).hasSize(1);
        tx().executeWithoutResult(status -> {
            CustomerOrder saved = em.find(CustomerOrder.class, order.id());
            WorkOrder work = em.find(WorkOrder.class, deposit.workOrderIds().getFirst());
            assertThat(saved.getPlannedStartDate()).isEqualTo(order.plannedStartDate());
            assertThat(saved.getPlannedEndDate()).isEqualTo(order.plannedEndDate());
            assertThat(saved.getEstimatedDueDate()).isEqualTo(order.dueDate());
            assertThat(work.getPlannedStartDate()).isEqualTo(today());
            assertThat(work.getUser()).isNotNull();
        });
        authenticateAs(UserRole.SUPER_ADMIN);
        Long workId = deposit.workOrderIds().getFirst();
        for (String state : List.of("PRODUCE", "PACK", "PACK_DONE")) {
            workOrderService.changeStatus(workId, new WorkOrderDto.UpdateStatusRequest(state, null), userId);
        }
        tx().executeWithoutResult(status -> assertThat(em.find(CustomerOrder.class, order.id())
                .getStatusCode().getCode()).isEqualTo("PACKING"));
    }

    @Test
    void revalidatesAtRegistrationAndAllowsExplicitSwitchToNormalSchedule() {
        QuotationDto.DetailResponse first = sameDayQuotation(1000);
        QuotationDto.DetailResponse second = sameDayQuotation(200);
        orderService.create(orderRequest(first, true), userId);

        assertThatThrownBy(() -> orderService.create(orderRequest(second, true), userId))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("오늘은 착수할 수 없습니다");
        var normal = orderService.create(orderRequest(second, false), userId);
        assertThat(normal.depositDueDate()).isEqualTo(today().plusDays(3));
        assertThat(normal.plannedStartDate()).isAfterOrEqualTo(today().plusDays(3));
    }

    @Test
    void serializesCompetingSameDayReservations() throws Exception {
        QuotationDto.DetailResponse first = sameDayQuotation(1000);
        QuotationDto.DetailResponse second = sameDayQuotation(1000);
        List<Object> results = registerConcurrently(orderRequest(first, true), orderRequest(second, true));
        assertThat(results.stream().filter(CustomerOrderDto.CreateResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IllegalArgumentException.class::isInstance).count()).isEqualTo(1);
    }

    private QuotationDto.DetailResponse sameDayQuotation(int quantity) {
        var preview = pdfStorageService.create(new QuotationDto.CreateRequest(clientId, dueDate(), null,
                false, List.of(item(quantity, 1000L)), null, true), userId);
        sendService.send(preview.quotationId(), new QuotationSendDto.Request(preview.confirmationToken()), userId);
        return statusService.approve(preview.quotationId(), userId);
    }

    private CustomerOrderDto.CreateRequest orderRequest(QuotationDto.DetailResponse quote, boolean sameDayStart) {
        return new CustomerOrderDto.CreateRequest(quote.id(),
                List.of(new CustomerOrderDto.ItemRequest(quote.items().getFirst().id(), (int) quote.totalQuantity())),
                quote.estimatedDueDate(), true, null, "수신자", "010-1234-5678", NotifyChannel.EMAIL, null, sameDayStart);
    }

    private void persistCode(CodeGroup group, String value) {
        CommonCode code = instantiate(CommonCode.class);
        ReflectionTestUtils.setField(code, "codeGroup", group);
        ReflectionTestUtils.setField(code, "code", value);
        ReflectionTestUtils.setField(code, "name", value);
        ReflectionTestUtils.setField(code, "sortOrder", 1);
        ReflectionTestUtils.setField(code, "status", CodeStatus.ACTIVE);
        em.persist(code);
    }
}
