package net.likelion.bebc25.prodflow.client.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import net.likelion.bebc25.prodflow.user.entity.User;
import net.likelion.bebc25.prodflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;


    @Transactional
    public ClientDto.DetailResponse createClient(ClientDto.CreateRequest request) {
        // Order 관련 임시값
        int totalOrder = 0;
        Long totalAmount = 0L;
        int ongoingOrders = 0;

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 사용자입니다. id = " + request.userId()));

        // 로그인 구현이 안되어있어 임시로 구현한 메소드
        Client client = clientRepository.save(request.toEntityWithoutLogin(user));
//        Client client = clientRepository.save(request.toEntity(user));
        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }

    public ClientDto.DetailResponse getClientDetail(Long clientId) {
        // Order 관련 임시값
        int totalOrder = 3;
        Long totalAmount = 12340000L;
        int ongoingOrders = 1;

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 거래처 입니다. id = " + clientId));
        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }

    public Page<ClientDto.Response> getClients(Pageable pageable) {
        Page<Client> clients;

        clients = clientRepository.findAll(pageable);

        return clients.map(client -> {
            // Order 관련 임시값
            int totalOrder = 3;
            Long totalAmount = 12340000L;
            LocalDateTime recentOrderedAt = LocalDateTime.now();

            return ClientDto.Response.from(
                    client,
                    totalOrder,
                    totalAmount,
                    recentOrderedAt
            );
        });
    }
}
