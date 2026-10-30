package net.likelion.bebc25.prodflow.client.service;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.client.dto.ClientDto;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.client.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientDto.DetailResponse getClientDetail(Long clientId) {
        // Order 관련 임시값
        int totalOrder = 3;
        Long totalAmount = 12340000L;
        int ongoingOrders = 1;

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 거래처 입니다. id = " + clientId));

        return ClientDto.DetailResponse.from(client, totalOrder, totalAmount, ongoingOrders);
    }
}
