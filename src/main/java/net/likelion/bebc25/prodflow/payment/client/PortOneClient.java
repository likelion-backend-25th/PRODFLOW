package net.likelion.bebc25.prodflow.payment.client;

import lombok.RequiredArgsConstructor;
import net.likelion.bebc25.prodflow.payment.properties.PortOneProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class PortOneClient {
    private final RestClient restClient;
    private final PortOneProperties properties;
}
