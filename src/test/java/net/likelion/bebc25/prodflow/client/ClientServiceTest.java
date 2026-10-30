package net.likelion.bebc25.prodflow.client;

import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.client.service.ClientService;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientService clientService;

    @Test
    @DisplayName("거래처 상세 조회 시 거래처 정보와 담당자 정보, 주문 통계를 반환한다")
    void getClientDetail() {
        // given
        Long clientId = 1L;
        Client client = mock(Client.class);
        Address address = new Address("06236", "서울특별시 강남구 테헤란로 152", "12층");
        User user = User.builder()
                .name("이서준")
                .phone("010-2000-0002")
                .build();

        given(clientRepository.findById(clientId)).willReturn(Optional.of(client));
        given(client.getCompany()).willReturn("한빛케이스(주)");
        given(client.getEmployee()).willReturn("최민재");
        given(client.getEmail()).willReturn("contact@hanbit-case.example");
        given(client.getPhone()).willReturn("02-6000-1101");
        given(client.getAddress()).willReturn(address);
        given(client.getUser()).willReturn(user);

        // when
        ClientDto.DetailResponse response = clientService.getClientDetail(clientId);

        // then
        assertThat(response.company()).isEqualTo("한빛케이스(주)");
        assertThat(response.employee()).isEqualTo("최민재");
        assertThat(response.email()).isEqualTo("contact@hanbit-case.example");
        assertThat(response.phone()).isEqualTo("02-6000-1101");
        assertThat(response.address()).isEqualTo("서울특별시 강남구 테헤란로 152, 12층");
        assertThat(response.userName()).isEqualTo("이서준");
        assertThat(response.userPhone()).isEqualTo("010-2000-0002");

        // 주문 통계는 현재 서비스에 정의된 임시값을 검증한다.
        assertThat(response.totalOrder()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualTo(12340000L);
        assertThat(response.ongoingOrders()).isEqualTo(1);
        verify(clientRepository).findById(clientId);
    }

    @Test
    @DisplayName("존재하지 않는 거래처 ID로 상세 조회하면 예외가 발생한다")
    void getClientDetailWithNonexistentId() {
        // given
        Long clientId = 999L;
        given(clientRepository.findById(clientId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> clientService.getClientDetail(clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않은 거래처 입니다. id = " + clientId);
        verify(clientRepository).findById(clientId);
    }
}
