package net.likelion.bebc25.prodflow.product;

import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.commoncode.entity.CommonCode;
import net.likelion.bebc25.prodflow.customer_order.entity.CustomerOrder;
import net.likelion.bebc25.prodflow.customer_order.entity.OrderProduct;
import net.likelion.bebc25.prodflow.customer_order.repository.OrderProductRepository;
import net.likelion.bebc25.prodflow.product.dto.ProductDto.OrderHistoryResponse;
import net.likelion.bebc25.prodflow.product.repository.ProductRepository;
import net.likelion.bebc25.prodflow.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ProductOrderHistoryServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    OrderProductRepository orderProductRepository;

    @InjectMocks
    ProductService productService;

    @Test
    @DisplayName("품목 수주 이력 조회")
    void getOrderHistories() {
        given(productRepository.existsById(1L)).willReturn(true);

        OrderProduct orderProduct = mock(OrderProduct.class);
        CustomerOrder order = mock(CustomerOrder.class);
        Client client = mock(Client.class);
        CommonCode status = mock(CommonCode.class);

        given(orderProduct.getCustomerOrder()).willReturn(order);
        given(orderProduct.getProductName()).willReturn("디자인 A 투명 케이스");
        given(orderProduct.getQuantity()).willReturn(100);
        given(orderProduct.getUnitPrice()).willReturn(10000L);
        given(orderProduct.lineAmount()).willReturn(1_000_000L);

        given(order.getId()).willReturn(13L);
        given(order.getOrderNo()).willReturn("PF-2026-013");
        given(order.getClient()).willReturn(client);
        given(client.getCompany()).willReturn("모바일(주)");
        given(order.getTotalAmount()).willReturn(1_100_000L);
        given(order.getEstimatedDueDate()).willReturn(LocalDate.of(2026, 11, 6));
        given(order.getStatusCode()).willReturn(status);
        given(status.getCode()).willReturn("WAITING");
        given(status.getName()).willReturn("생산대기");

        given(orderProductRepository.findRecentByProductId(eq(1L), any(Pageable.class)))
                .willReturn(List.of(orderProduct));

        List<OrderHistoryResponse> result = productService.getOrderHistories(1L, 3);

        assertThat(result).hasSize(1);
        OrderHistoryResponse history = result.getFirst();
        assertThat(history.orderNo()).isEqualTo("PF-2026-013");
        assertThat(history.clientName()).isEqualTo("모바일(주)");
        assertThat(history.productName()).isEqualTo("디자인 A 투명 케이스");
        assertThat(history.quantity()).isEqualTo(100);
        assertThat(history.statusCode()).isEqualTo("WAITING");
        assertThat(history.statusName()).isEqualTo("생산대기");
    }
}
