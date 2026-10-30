package net.likelion.bebc25.prodflow.client.dto;

import net.likelion.bebc25.prodflow.client.entity.Client;

public class ClientDto {
    public record DetailResponse(
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
