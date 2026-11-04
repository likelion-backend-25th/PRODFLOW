package net.likelion.bebc25.prodflow.client;

import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.client.service.ClientService;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.customer_order.repository.CustomerOrderRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest {
    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerOrderRepository customerOrderRepository;

    @InjectMocks
    private ClientService clientService;

    private ClientService securedClientService;

    @BeforeEach
    void setUpMethodSecurity() {
        SecurityContextHolder.clearContext();
        // Mockito로 생성한 서비스에도 @PreAuthorize를 적용한다.
        ProxyFactory proxyFactory = new ProxyFactory(clientService);
        proxyFactory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        securedClientService = (ClientService) proxyFactory.getProxy();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "test-user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }

    @Test
    @DisplayName("검색어가 null이면 전체 거래처 목록과 주문 통계를 반환하고 페이지 정보와 순서를 유지한다")
    void findClients() {
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
        given(customerOrderRepository.countByClientId(3L)).willReturn(8L);
        given(customerOrderRepository.sumTotalAmountByClientId(3L)).willReturn(98000000L);
        given(customerOrderRepository.countByClientId(2L)).willReturn(0L);
        given(customerOrderRepository.sumTotalAmountByClientId(2L)).willReturn(0L);

        // when
        LocalDateTime before = LocalDateTime.now();
        Page<ClientDto.Response> response = clientService.findClients(null, pageable);
        LocalDateTime after = LocalDateTime.now();

        // then
        assertThat(response.getContent())
                .extracting(ClientDto.Response::id, ClientDto.Response::company, ClientDto.Response::employee,
                        ClientDto.Response::phone, ClientDto.Response::email)
                .containsExactly(
                        tuple(3L, "한빛케이스(주)", "최민재", "02-6000-1101", "contact@hanbit-case.example"),
                        tuple(2L, "세원모바일(주)", "오지훈", "031-210-2202", "sales@sewon-mobile.example")
                );
        assertThat(response.getContent())
                .extracting(ClientDto.Response::totalOrder, ClientDto.Response::totalAmount)
                .containsExactly(tuple(8L, 98000000L), tuple(0L, 0L));
        // 최근 주문일은 현재 서비스에서 조회 시각을 반환한다.
        assertThat(response.getContent()).allSatisfy(client -> {
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
        verify(customerOrderRepository).countByClientId(3L);
        verify(customerOrderRepository).sumTotalAmountByClientId(3L);
        verify(customerOrderRepository).countByClientId(2L);
        verify(customerOrderRepository).sumTotalAmountByClientId(2L);
        verifyNoMoreInteractions(customerOrderRepository);
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"한빛", "", " "})
    @DisplayName("검색어가 있으면 빈 문자열과 공백을 포함하여 그대로 검색하고 결과와 페이지 정보를 반환한다")
    void findClientsWithKeyword(String keyword) {
        // given
        Pageable pageable = PageRequest.of(1, 1, Sort.by("id").descending());
        Client client = new Client(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example", "02-6000-1101",
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층"),
                User.builder().name("이서준").phone("010-2000-0002").build()
        );
        ReflectionTestUtils.setField(client, "id", 2L);
        given(clientRepository.findByKeyword(keyword, pageable))
                .willReturn(new PageImpl<>(List.of(client), pageable, 3L));
        given(customerOrderRepository.countByClientId(2L)).willReturn(6L);
        given(customerOrderRepository.sumTotalAmountByClientId(2L)).willReturn(45000000L);

        // when
        LocalDateTime before = LocalDateTime.now();
        Page<ClientDto.Response> response = clientService.findClients(keyword, pageable);
        LocalDateTime after = LocalDateTime.now();

        // then
        assertThat(response.getContent()).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(2L);
            assertThat(result.company()).isEqualTo("한빛케이스(주)");
            assertThat(result.employee()).isEqualTo("최민재");
            assertThat(result.phone()).isEqualTo("02-6000-1101");
            assertThat(result.email()).isEqualTo("contact@hanbit-case.example");
            assertThat(result.totalOrder()).isEqualTo(6L);
            assertThat(result.totalAmount()).isEqualTo(45000000L);
            assertThat(result.recentOrderedAt()).isBetween(before, after);
        });
        assertThat(response.getPageable()).isEqualTo(pageable);
        assertThat(response.getNumber()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(3L);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        verify(clientRepository).findByKeyword(keyword, pageable);
        verify(customerOrderRepository).countByClientId(2L);
        verify(customerOrderRepository).sumTotalAmountByClientId(2L);
        verifyNoMoreInteractions(customerOrderRepository);
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"없는거래처"})
    @DisplayName("전체 조회나 키워드 검색 결과가 없으면 페이지 정보를 유지한 빈 목록을 반환한다")
    void findClientsWhenEmpty(String keyword) {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        if (keyword == null) {
            given(clientRepository.findAll(pageable)).willReturn(Page.empty(pageable));
        } else {
            given(clientRepository.findByKeyword(keyword, pageable)).willReturn(Page.empty(pageable));
        }

        // when
        Page<ClientDto.Response> response = clientService.findClients(keyword, pageable);

        // then
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPageable()).isEqualTo(pageable);
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
        if (keyword == null) {
            verify(clientRepository).findAll(pageable);
        } else {
            verify(clientRepository).findByKeyword(keyword, pageable);
        }
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository, customerOrderRepository);
    }

    @ParameterizedTest
    @CsvSource({
            "SUPER_ADMIN, 0, 0, 0",
            "ORDER_MANAGER, 0, 0, 0",
            "SUPER_ADMIN, 5, 45000000, 2",
            "ORDER_MANAGER, 5, 45000000, 2"
    })
    @DisplayName("거래처 등록 후 저장된 ID로 집계한 주문 통계와 상세 정보를 반환한다")
    void createClient(String role, Long totalOrder, Long totalAmount, Long ongoingOrders) {
        // given
        authenticate(role);
        Long userId = 2L;
        Address address = new Address("06236", "서울특별시 강남구 테헤란로 152", "12층");
        ClientDto.Request request = new ClientDto.Request(
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
        givenOrderStatistics(10L, totalOrder, totalAmount, ongoingOrders);

        // when
        ClientDto.DetailResponse response = securedClientService.createClient(request);

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
        // 감사 정보는 JPA Auditing에서 처리하며 임시 이름을 직접 설정하지 않는다.
        assertThat(savedClient.getCreatedBy()).isNull();
        assertThat(savedClient.getUpdatedBy()).isNull();

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.company()).isEqualTo(request.company());
        assertThat(response.employee()).isEqualTo(request.employee());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.phone()).isEqualTo(request.phone());
        assertThat(response.address()).isEqualTo("서울특별시 강남구 테헤란로 152, 12층");
        assertThat(response.userName()).isEqualTo("이서준");
        assertThat(response.userPhone()).isEqualTo("010-2000-0002");
        assertThat(response.totalOrder()).isEqualTo(totalOrder);
        assertThat(response.totalAmount()).isEqualTo(totalAmount);
        assertThat(response.ongoingOrders()).isEqualTo(ongoingOrders);
        verify(userRepository).findById(userId);
        verifyOrderStatistics(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "ORDER_MANAGER"})
    @DisplayName("존재하지 않는 담당자 ID로 거래처를 등록하면 예외가 발생하고 저장하지 않는다")
    void createClientWithNonexistentUserId(String role) {
        // given
        authenticate(role);
        Long userId = 999L;
        ClientDto.Request request = new ClientDto.Request(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example",
                "02-6000-1101", userId,
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층")
        );
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> securedClientService.createClient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않은 사용자입니다. id = " + userId);
        verify(userRepository).findById(userId);
        verifyNoInteractions(clientRepository, customerOrderRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCTION_MANAGER", "INQUIRY_MANAGER"})
    @DisplayName("거래처 등록 권한이 없는 역할은 담당자 조회와 저장 전에 거부된다")
    void createClientWithUnauthorizedRole(String role) {
        // given
        authenticate(role);
        ClientDto.Request request = new ClientDto.Request(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example",
                "02-6000-1101", 2L,
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층")
        );

        // when & then
        assertThatThrownBy(() -> securedClientService.createClient(request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository, clientRepository, customerOrderRepository);
    }

    @Test
    @DisplayName("미인증 사용자는 거래처를 등록할 수 없다")
    void createClientWithoutAuthentication() {
        // given
        ClientDto.Request request = new ClientDto.Request(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example",
                "02-6000-1101", 2L,
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층")
        );

        // when & then
        assertThatThrownBy(() -> securedClientService.createClient(request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(userRepository, clientRepository, customerOrderRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "ORDER_MANAGER"})
    @DisplayName("최고관리자와 수주관리자는 거래처 정보와 담당자를 수정하고 상세 정보와 주문 통계를 받는다")
    void updateClient(String role) {
        // given
        authenticate(role);
        Long clientId = 1L;
        Client client = existingClientForUpdate(clientId);
        ClientDto.Request request = clientUpdateRequest();
        User newUser = User.builder().name("박하은").phone("010-3000-0003").build();
        given(clientRepository.findById(clientId)).willReturn(Optional.of(client));
        given(userRepository.findById(request.userId())).willReturn(Optional.of(newUser));
        givenOrderStatistics(clientId, 9L, 78000000L, 4L);

        // when
        ClientDto.DetailResponse response = securedClientService.updateClient(clientId, request);

        // then
        assertThat(client.getId()).isEqualTo(clientId);
        assertThat(client.getCompany()).isEqualTo(request.company());
        assertThat(client.getEmployee()).isEqualTo(request.employee());
        assertThat(client.getEmail()).isEqualTo(request.email());
        assertThat(client.getPhone()).isEqualTo(request.phone());
        assertThat(client.getAddress()).isEqualTo(request.address());
        assertThat(client.getUser()).isSameAs(newUser);

        assertThat(response.id()).isEqualTo(clientId);
        assertThat(response.company()).isEqualTo(request.company());
        assertThat(response.employee()).isEqualTo(request.employee());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.phone()).isEqualTo(request.phone());
        assertThat(response.address()).isEqualTo("경기도 수원시 영통구 삼성로 129, 본관 3층");
        assertThat(response.userName()).isEqualTo("박하은");
        assertThat(response.userPhone()).isEqualTo("010-3000-0003");
        assertThat(response.totalOrder()).isEqualTo(9L);
        assertThat(response.totalAmount()).isEqualTo(78000000L);
        assertThat(response.ongoingOrders()).isEqualTo(4L);
        verify(clientRepository).findById(clientId);
        verify(userRepository).findById(request.userId());
        verifyOrderStatistics(clientId);
        verifyNoMoreInteractions(clientRepository, userRepository);
    }

    @Test
    @DisplayName("존재하지 않는 거래처를 수정하면 예외가 발생하고 담당자를 조회하지 않는다")
    void updateClientWithNonexistentClientId() {
        // given
        authenticate("ORDER_MANAGER");
        Long clientId = 999L;
        ClientDto.Request request = clientUpdateRequest();
        given(clientRepository.findById(clientId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> securedClientService.updateClient(clientId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 거래처 입니다. id = " + clientId);
        verify(clientRepository).findById(clientId);
        verifyOrderStatistics(clientId);
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("존재하지 않는 담당자로 수정하면 예외가 발생하고 기존 거래처 정보를 유지한다")
    void updateClientWithNonexistentUserId() {
        // given
        authenticate("ORDER_MANAGER");
        Long clientId = 1L;
        Client client = existingClientForUpdate(clientId);
        Address originalAddress = client.getAddress();
        User originalUser = client.getUser();
        ClientDto.Request request = clientUpdateRequest();
        given(clientRepository.findById(clientId)).willReturn(Optional.of(client));
        given(userRepository.findById(request.userId())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> securedClientService.updateClient(clientId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않은 사용자입니다. id = " + request.userId());
        assertThat(client.getId()).isEqualTo(clientId);
        assertThat(client.getCompany()).isEqualTo("한빛케이스(주)");
        assertThat(client.getEmployee()).isEqualTo("최민재");
        assertThat(client.getEmail()).isEqualTo("contact@hanbit-case.example");
        assertThat(client.getPhone()).isEqualTo("02-6000-1101");
        assertThat(client.getAddress()).isSameAs(originalAddress);
        assertThat(client.getUser()).isSameAs(originalUser);
        verify(clientRepository).findById(clientId);
        verify(userRepository).findById(request.userId());
        verifyOrderStatistics(clientId);
        verifyNoMoreInteractions(clientRepository, userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCTION_MANAGER", "INQUIRY_MANAGER"})
    @DisplayName("거래처 수정 권한이 없는 역할은 거래처와 담당자 조회 전에 거부된다")
    void updateClientWithUnauthorizedRole(String role) {
        // given
        authenticate(role);
        ClientDto.Request request = clientUpdateRequest();

        // when & then
        assertThatThrownBy(() -> securedClientService.updateClient(1L, request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(clientRepository, userRepository, customerOrderRepository);
    }

    @Test
    @DisplayName("미인증 사용자는 거래처를 수정할 수 없다")
    void updateClientWithoutAuthentication() {
        // given
        ClientDto.Request request = clientUpdateRequest();

        // when & then
        assertThatThrownBy(() -> securedClientService.updateClient(1L, request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(clientRepository, userRepository, customerOrderRepository);
    }

    private void givenOrderStatistics(Long clientId, Long totalOrder, Long totalAmount, Long ongoingOrders) {
        given(customerOrderRepository.countByClientId(clientId)).willReturn(totalOrder);
        given(customerOrderRepository.sumTotalAmountByClientId(clientId)).willReturn(totalAmount);
        given(customerOrderRepository.countActiveOrdersByClientId(clientId)).willReturn(ongoingOrders);
    }

    private void verifyOrderStatistics(Long clientId) {
        verify(customerOrderRepository).countByClientId(clientId);
        verify(customerOrderRepository).sumTotalAmountByClientId(clientId);
        verify(customerOrderRepository).countActiveOrdersByClientId(clientId);
        verifyNoMoreInteractions(customerOrderRepository);
    }

    private Client existingClientForUpdate(Long clientId) {
        Client client = new Client(
                "한빛케이스(주)", "최민재", "contact@hanbit-case.example", "02-6000-1101",
                new Address("06236", "서울특별시 강남구 테헤란로 152", "12층"),
                User.builder().name("이서준").phone("010-2000-0002").build()
        );
        ReflectionTestUtils.setField(client, "id", clientId);
        return client;
    }

    private ClientDto.Request clientUpdateRequest() {
        return new ClientDto.Request(
                "세원모바일(주)", "오지훈", "sales@sewon-mobile.example", "031-210-2202", 3L,
                new Address("16677", "경기도 수원시 영통구 삼성로 129", "본관 3층")
        );
    }

    @ParameterizedTest
    @CsvSource({"7, 98000000, 2", "0, 0, 0", "3000000000, 9000000000, 2500000000"})
    @DisplayName("거래처 상세 조회 시 거래처 정보와 담당자 정보, 집계된 Long 주문 통계를 반환한다")
    void getClientDetail(Long totalOrder, Long totalAmount, Long ongoingOrders) {
        // given
        Long clientId = 1L;
        Client client = existingClientForUpdate(clientId);

        given(clientRepository.findById(clientId)).willReturn(Optional.of(client));
        givenOrderStatistics(clientId, totalOrder, totalAmount, ongoingOrders);

        // when
        ClientDto.DetailResponse response = clientService.getClientDetail(clientId);

        // then
        assertThat(response.id()).isEqualTo(clientId);
        assertThat(response.company()).isEqualTo("한빛케이스(주)");
        assertThat(response.employee()).isEqualTo("최민재");
        assertThat(response.email()).isEqualTo("contact@hanbit-case.example");
        assertThat(response.phone()).isEqualTo("02-6000-1101");
        assertThat(response.address()).isEqualTo("서울특별시 강남구 테헤란로 152, 12층");
        assertThat(response.userName()).isEqualTo("이서준");
        assertThat(response.userPhone()).isEqualTo("010-2000-0002");

        assertThat(response.totalOrder()).isEqualTo(totalOrder);
        assertThat(response.totalAmount()).isEqualTo(totalAmount);
        assertThat(response.ongoingOrders()).isEqualTo(ongoingOrders);
        verify(clientRepository).findById(clientId);
        verifyOrderStatistics(clientId);
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository);
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
        verifyOrderStatistics(clientId);
        verifyNoMoreInteractions(clientRepository);
        verifyNoInteractions(userRepository);
    }
}
