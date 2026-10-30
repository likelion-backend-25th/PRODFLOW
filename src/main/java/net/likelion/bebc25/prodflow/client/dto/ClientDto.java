package net.likelion.bebc25.prodflow.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.user.entity.User;

@ToString
public class ClientDto {
    public record CreateRequest(
            String company,
            String employee,
            String email,
            String phone,
            Long userId,
            @JsonProperty
            Address address
    ) {
        public Client toEntity(User user) {
            return new Client(company, employee, email, phone, address, user);
        }

        public Client toEntityWithoutLogin(User user) {
            Client client = new Client(company, employee, email, phone, address, user);
            client.setCreatedBy("TEST_NAME");
            client.setUpdatedBy("TEST_NAME");
            return client;
        }
    }

    public record DetailResponse(
            Long id,
            String company,
            String employee,
            String email,
            String phone,
            String address,
            String userName,
            String userPhone,
            int totalOrder,
            Long totalAmount,
            int ongoingOrders
    ) {
        public static DetailResponse from(Client client, int totalOrder, Long totalAmount, int ongoingOrders) {
            return new DetailResponse(
                    client.getId(),
                    client.getCompany(),
                    client.getEmployee(),
                    client.getEmail(),
                    client.getPhone(),
                    client.getAddress().getRoadAddress() + ", " + client.getAddress().getDetailAddress(),
                    client.getUser().getName(),
                    client.getUser().getPhone(),
                    totalOrder,
                    totalAmount,
                    ongoingOrders
            );
        }
    }
}
