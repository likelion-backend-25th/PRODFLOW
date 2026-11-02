package net.likelion.bebc25.prodflow.quotation;

import net.likelion.bebc25.prodflow.quotation.repository.QuotationRepository;
import net.likelion.bebc25.prodflow.quotation.repository.QuotationScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quotation-seed;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,LINE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=always",
        "spring.jpa.properties.hibernate.show_sql=false",
        "jwt.secret=cXVvdGF0aW9uLXRlc3Qtc2VjcmV0LW11c3QtYmUtMzItYnl0ZXM=",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
@Transactional
class QuotationSeedTest {
    @Autowired QuotationRepository quotations;
    @Autowired QuotationScheduleRepository schedules;

    @Test
    void seedLoadsWithContactSnapshotsAndExistingProductionReadyOrdersAreIncluded() {
        assertThat(quotations.count()).isEqualTo(16);
        assertThat(quotations.findAll()).allSatisfy(quotation -> {
            assertThat(quotation.getContactName()).isNotBlank();
            assertThat(quotation.getAddress()).isNotBlank();
            assertThat(quotation.getEmail()).isNotBlank();
            assertThat(quotation.getPhone()).isNotBlank();
        });
        var orders = schedules.findActiveOrders();
        assertThat(orders).anyMatch(order -> "PRODUCTION_READY".equals(order.getStatusCode().getCode()));
        var ids = orders.stream().map(order -> order.getId()).toList();
        assertThat(schedules.findOrderProducts(ids)).isNotEmpty();
        assertThat(schedules.findFixedPlans(ids)).isNotEmpty();
    }
}
