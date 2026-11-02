package net.likelion.bebc25.prodflow.client;

import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.client.service.ClientService;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {
    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClientService clientService;

    @Test
    @DisplayName("거래처 목록 조회 시 상세 정보와 주문 통계를 반환하고 페이지 정보와 순서를 유지한다")
    void getClients() {
        // given
        Pageable pageable = PageRequest.of(1, 2, Sort.by("id").descending());
        Address address = new Address("06236", "서울특별시 강남구 테헤란로 152", "12층");
        User user = User.builder().name("이서준").phone("010-2000-0002").build();
        Client firstClient = new Client(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example", "02-6000-1101", address, user
        );
        Client secondClient = new Client(
                "세원모바일(주)", "오지훈", "sales@sewon-mobile.example", "031-210-2202", address, user
        );
        ReflectionTestUtils.setField(firstClient, "id", 3L);
        ReflectionTestUtils.setField(secondClient, "id", 2L);
        given(clientRepository.findAll(pageable))
                .willReturn(new PageImpl<>(List.of(firstClient, secondClient), pageable, 5L));

        // when
        LocalDateTime before = LocalDateTime.now();
        Page<ClientDto.Response> response = clientService.getClients(pageable);
        LocalDateTime after = LocalDateTime.now();

        // then
        assertThat(response.getContent())
                .extracting(ClientDto.Response::id, ClientDto.Response::company, ClientDto.Response::employee,
                        ClientDto.Response::phone, ClientDto.Response::email)
                .containsExactly(
                        tuple(3L, "한빛케이스(주)", "최민재", "02-6000-1101", "contact@hanbit-case.example"),
                        tuple(2L, "세원모바일(주)", "오지훈", "031-210-2202", "sales@sewon-mobile.example")
                );
        // 주문 통계와 최근 주문일은 현재 서비스에 정의된 임시값을 검증한다.
        assertThat(response.getContent()).allSatisfy(client -> {
            assertThat(client.totalOrder()).isEqualTo(3);
            assertThat(client.totalAmount()).isEqualTo(12340000L);
            assertThat(client.recentOrderedAt()).isBetween(before, after);
        });
        assertThat(response.getPageable()).isEqualTo(pageable);
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getNumberOfElements()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(5L);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        verify(clientRepository).findAll(pageable);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("등록된 거래처가 없으면 페이지 정보를 유지한 빈 목록을 반환한다")
    void getClientsWhenEmpty() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        given(clientRepository.findAll(pageable)).willReturn(Page.empty(pageable));

        // when
        Page<ClientDto.Response> response = clientService.getClients(pageable);

        // then
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPageable()).isEqualTo(pageable);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
        verify(clientRepository).findAll(pageable);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("거래처 등록 시 요청 정보와 담당자를 저장하고 상세 정보와 초기 주문 통계를 반환한다")
    void createClient() {
        // given
        Long userId = 2L;
        Address address = new Address("06236", "서울특별시 강남구 테헤란로 152", "12층");
        ClientDto.CreateRequest request = new ClientDto.CreateRequest(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example",
                "02-6000-1101", userId, address
        );
        User user = User.builder()
                .name("이서준")
                .phone("010-2000-0002")
                .build();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(clientRepository.save(any(Client.class))).willAnswer(invocation -> {
            Client client = invocation.getArgument(0);
            ReflectionTestUtils.setField(client, "id", 10L);
            return client;
        });

        // when
        ClientDto.DetailResponse response = clientService.createClient(request);

        // then
        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(clientCaptor.capture());
        Client savedClient = clientCaptor.getValue();
        assertThat(savedClient.getCompany()).isEqualTo(request.company());
        assertThat(savedClient.getEmployee()).isEqualTo(request.employee());
        assertThat(savedClient.getEmail()).isEqualTo(request.email());
        assertThat(savedClient.getPhone()).isEqualTo(request.phone());
        assertThat(savedClient.getAddress()).isEqualTo(address);
        assertThat(savedClient.getUser()).isSameAs(user);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.company()).isEqualTo(request.company());
        assertThat(response.employee()).isEqualTo(request.employee());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.phone()).isEqualTo(request.phone());
        assertThat(response.address()).isEqualTo("서울특별시 강남구 테헤란로 152, 12층");
        assertThat(response.userName()).isEqualTo("이서준");
        assertThat(response.userPhone()).isEqualTo("010-2000-0002");
        assertThat(response.totalOrder()).isZero();
        assertThat(response.totalAmount()).isEqualTo(0L);
        assertThat(response.ongoingOrders()).isZero();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("존재하지 않는 담당자 ID로 거래처를 등록하면 예외가 발생하고 저장하지 않는다")
    void createClientWithNonexistentUserId() {
        // given
        Long userId = 999L;
        ClientDto.CreateRequest request = new ClientDto.CreateRequest(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example",
                "02-6000-1101", userId,
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층")
        );
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않은 사용자입니다. id = " + userId);
        verify(userRepository).findById(userId);
        verifyNoInteractions(clientRepository);
    }

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
