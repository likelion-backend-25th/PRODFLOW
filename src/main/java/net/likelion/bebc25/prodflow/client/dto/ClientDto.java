package net.likelion.bebc25.prodflow.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDateTime;

@ToString
public class ClientDto {
    public record Request(
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
    }

    public record Response(
            Long id,
            String company,
            String employee,
            String phone,
            String email,
            int totalOrder,
            Long totalAmount,
            LocalDateTime recentOrderedAt
    ) {
        public static Response from(Client client, int totalOrder, Long totalAmount, LocalDateTime recentOrderedAt) {
            return new Response(
                    client.getId(),
                    client.getCompany(),
                    client.getEmployee(),
                    client.getPhone(),
                    client.getEmail(),
                    totalOrder,
                    totalAmount,
                    recentOrderedAt
            );
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

    public record ImportResponse(
            Long id,
            String company
    ) {
        public static ImportResponse from(Long id, String company) {
            return new ImportResponse(id, company);
        }
    }
}
