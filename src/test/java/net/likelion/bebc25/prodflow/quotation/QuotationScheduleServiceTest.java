package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.productionline.entity.Line;
import net.likelion.bebc25.prodflow.productionline.enums.LineStatus;
import net.likelion.bebc25.prodflow.productionline.repsitory.LineRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import net.likelion.bebc25.prodflow.quotation.service.QuotationScheduleService;
import net.likelion.bebc25.prodflow.workorder.entity.ProductionPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotationScheduleServiceTest {
    private final LineRepository lines = Mockito.mock(LineRepository.class);
    private final QuotationScheduleRepository repository = Mockito.mock(QuotationScheduleRepository.class);
    private final QuotationScheduleService service = new QuotationScheduleService(lines, repository);
    private final LocalDate today = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setUp() {
        when(lines.findAll()).thenReturn(List.of(new Line("라인", 100)));
        when(repository.findActiveOrders()).thenReturn(List.of());
        when(repository.findHolidays()).thenReturn(List.of());
    }

    @Test
    void skipsWeekendsAndLeavesThreeWorkingDaysAfterProduction() {
        var result = service.calculate(100, date(10), today);
        assertThat(result.plannedStartDate()).isEqualTo(date(5));
        assertThat(result.plannedEndDate()).isEqualTo(date(5));
        assertThat(result.estimatedDueDate()).isEqualTo(date(10));
    }

    @Test
    void excludesHolidaysFromProductionAndDeliveryBuffer() {
        when(repository.findHolidays()).thenReturn(List.of(date(5), date(9)));
        var result = service.calculate(100, date(12), today);
        assertThat(result.plannedStartDate()).isEqualTo(date(6));
        assertThat(result.estimatedDueDate()).isEqualTo(date(12));
    }

    @Test
    void placesLateOrdersNearTheirDueDateLeavingEarlierCapacityFree() {
        CustomerOrder order = order(1L, date(20));
        OrderProduct item = item(order, 100);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L))).thenReturn(List.of(item));
        var result = service.calculate(100, date(10), today);
        assertThat(result.plannedStartDate()).isEqualTo(date(5));
    }

    @Test
    void registeredOrdersConsumeCapacityBeforeNewQuotation() {
        CustomerOrder order = order(1L, date(10));
        OrderProduct item = item(order, 100);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L))).thenReturn(List.of(item));
        assertThatThrownBy(() -> service.calculate(100, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-11-11");
        assertThat(service.calculate(100, date(11), today).plannedStartDate()).isEqualTo(date(6));
    }

    @Test
    void fixedPlansKeepTheirDatesAndPastQuantitiesAreNotScheduledAgain() {
        CustomerOrder order = order(1L, date(10));
        OrderProduct item = item(order, 200);
        ProductionPlan past = plan(item, today.minusDays(1), 100);
        ProductionPlan future = plan(item, date(5), 100);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L))).thenReturn(List.of(item));
        when(repository.findFixedPlans(List.of(1L))).thenReturn(List.of(past, future));
        assertThat(service.calculate(100, date(11), today).plannedStartDate()).isEqualTo(date(6));
    }

    @Test
    void rejectsImpossibleDueDateWithEarliestAlternative() {
        assertThatThrownBy(() -> service.calculate(100, date(9), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-11-10");
    }

    @Test
    void onlyAvailableAndOperatingLinesCountTowardCapacity() {
        Line broken = new Line("고장", 10000);
        broken.changeStatus(LineStatus.BROKEN);
        Line inspection = new Line("점검", 10000);
        inspection.changeStatus(LineStatus.INSPECTION);
        Line operating = new Line("가동", 100);
        operating.changeStatus(LineStatus.OPERATING);
        when(lines.findAll()).thenReturn(List.of(broken, inspection, operating));
        assertThatThrownBy(() -> service.calculate(200, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-11-11");
        when(lines.findAll()).thenReturn(List.of(broken, inspection));
        assertThatThrownBy(() -> service.calculate(100, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("가동 가능한 생산라인");
    }

    @Test
    void spreadsQuantityAcrossSeveralWorkingDays() {
        var result = service.calculate(250, date(12), today);
        assertThat(result.plannedStartDate()).isEqualTo(date(5));
        assertThat(result.plannedEndDate()).isEqualTo(date(9));
        assertThat(result.estimatedDueDate()).isEqualTo(date(12));
    }

    @Test
    void placesQuotationNearRequestedDueDateWhenThereIsTime() {
        var result = service.calculate(100, date(20), today);
        assertThat(result.plannedStartDate()).isEqualTo(date(17));
        assertThat(result.plannedEndDate()).isEqualTo(date(17));
        assertThat(result.estimatedDueDate()).isEqualTo(date(20));
    }

    @Test
    void movesWeekendDepositDeadlineToNextWorkingDay() {
        LocalDate wednesday = date(4);
        var result = service.calculate(100, date(12), wednesday);
        assertThat(result.plannedStartDate()).isEqualTo(date(9));
        assertThat(result.plannedEndDate()).isEqualTo(date(9));
    }

    @Test
    void reservesMultipleOrdersInRepositoryOrderBeforeNewQuotation() {
        CustomerOrder first = order(1L, date(10));
        CustomerOrder second = order(2L, date(11));
        OrderProduct firstItem = item(first, 100);
        OrderProduct secondItem = item(second, 100);
        when(repository.findActiveOrders()).thenReturn(List.of(first, second));
        when(repository.findOrderProducts(List.of(1L, 2L)))
                .thenReturn(List.of(firstItem, secondItem));
        assertThatThrownBy(() -> service.calculate(100, date(11), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-11-12");
        assertThat(service.calculate(100, date(12), today).plannedStartDate()).isEqualTo(date(9));
    }

    @Test
    void addsAllProductsOfExistingOrderAndUsesRemainingDailyCapacity() {
        CustomerOrder order = order(1L, date(10));
        OrderProduct firstItem = item(order, 30);
        OrderProduct secondItem = item(order, 40);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L)))
                .thenReturn(List.of(firstItem, secondItem));
        assertThat(service.calculate(30, date(10), today).plannedStartDate()).isEqualTo(date(5));
        assertThatThrownBy(() -> service.calculate(31, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-11-11");
    }

    @Test
    void schedulesOnlyQuantityNotAlreadyInFixedPlans() {
        CustomerOrder order = order(1L, date(11));
        OrderProduct item = item(order, 200);
        ProductionPlan pastPlan = plan(item, today.minusDays(1), 100);
        ProductionPlan futurePlan = plan(item, date(5), 50);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L))).thenReturn(List.of(item));
        when(repository.findFixedPlans(List.of(1L)))
                .thenReturn(List.of(pastPlan, futurePlan));
        // 남은 수주 수량 50개는 6일에 배치된다. 견적은 같은 날 남은 50개를 쓴다.
        assertThat(service.calculate(50, date(11), today).plannedStartDate()).isEqualTo(date(6));
    }

    @Test
    void rejectsFixedPlansExceedingOrderQuantity() {
        CustomerOrder order = order(1L, date(10));
        OrderProduct item = item(order, 100);
        ProductionPlan excessivePlan = plan(item, date(5), 101);
        when(repository.findActiveOrders()).thenReturn(List.of(order));
        when(repository.findOrderProducts(List.of(1L))).thenReturn(List.of(item));
        when(repository.findFixedPlans(List.of(1L))).thenReturn(List.of(excessivePlan));
        assertThatThrownBy(() -> service.calculate(100, date(11), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("수주 수량을 초과");
    }

    @Test
    void quotationCalculationsDoNotReserveCapacityForOtherQuotations() {
        var first = service.calculate(100, date(10), today);
        var second = service.calculate(100, date(10), today);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void rejectsNonPositiveQuantityAndSchedulesBeyondCalculationLimit() {
        assertThatThrownBy(() -> service.calculate(0, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("양수");
        assertThatThrownBy(() -> service.calculate(-1, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("양수");
        assertThatThrownBy(() -> service.calculate(Long.MAX_VALUE, date(10), today))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20년");
    }

    @Test
    void finishesDeliveryBeforeWeekendDueDateWithoutRejectingPossibleSchedule() {
        for (LocalDate dueDate : List.of(date(14), date(15))) {
            var result = service.calculate(100, dueDate, today);
            assertThat(result.plannedStartDate()).isEqualTo(date(10));
            assertThat(result.plannedEndDate()).isEqualTo(date(10));
            assertThat(result.estimatedDueDate()).isEqualTo(dueDate);
        }
    }

    @Test
    void finishesDeliveryBeforeHolidayDueDate() {
        when(repository.findHolidays()).thenReturn(List.of(date(12)));
        var result = service.calculate(100, date(12), today);
        assertThat(result.plannedEndDate()).isEqualTo(date(6));
        assertThat(result.estimatedDueDate()).isEqualTo(date(12));
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 11, day);
    }

    private CustomerOrder order(Long id, LocalDate due) {
        CustomerOrder order = mock(CustomerOrder.class);
        when(order.getId()).thenReturn(id);
        when(order.getDepositDueDate()).thenReturn(date(5));
        when(order.getEstimatedDueDate()).thenReturn(due);
        return order;
    }

    private OrderProduct item(CustomerOrder order, int quantity) {
        OrderProduct item = mock(OrderProduct.class);
        when(item.getCustomerOrder()).thenReturn(order);
        when(item.getQuantity()).thenReturn(quantity);
        return item;
    }

    private ProductionPlan plan(OrderProduct item, LocalDate date, int quantity) {
        ProductionPlan plan = mock(ProductionPlan.class);
        when(plan.getOrderProduct()).thenReturn(item);
        when(plan.getPlanDate()).thenReturn(date);
        when(plan.getQuantity()).thenReturn(quantity);
        return plan;
    }
}
