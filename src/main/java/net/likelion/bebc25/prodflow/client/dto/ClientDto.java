package net.likelion.bebc25.prodflow.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.ToString;
import net.likelion.bebc25.prodflow.client.entity.Client;
import net.likelion.bebc25.prodflow.common.domain.Address;
import net.likelion.bebc25.prodflow.user.entity.User;

import java.time.LocalDateTime;

@ToString
public class ClientDto {
    public record Request(
            @NotBlank(message = "거래처명을 입력해주세요.")
            String company,

            @NotBlank(message = "거래처 대표 직원 이름을 입력해주세요.")
            String employee,

            @NotBlank(message = "거래처 이메일을 입력해주세요.")
            String email,

            @NotBlank(message = "거래처 연락처를 입력해주세요.")
            String phone,

            @NotNull
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
            Long totalOrder,
            Long totalAmount,
            LocalDateTime recentOrderedAt
    ) {
        public static Response from(Client client, Long totalOrder, Long totalAmount, LocalDateTime recentOrderedAt) {
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
            String zipcode,
            String roadAddress,
            String detailAddress,
            String userName,
            String userPhone,
            Long totalOrder,
            Long totalAmount,
            Long ongoingOrders
    ) {
        public static DetailResponse from(Client client, Long totalOrder, Long totalAmount, Long ongoingOrders) {
            return new DetailResponse(
                    client.getId(),
                    client.getCompany(),
                    client.getEmployee(),
                    client.getEmail(),
                    client.getPhone(),
                    client.getAddress().getRoadAddress() + ", " + client.getAddress().getDetailAddress(),
                    client.getAddress().getZipcode(),
                    client.getAddress().getRoadAddress(),
                    client.getAddress().getDetailAddress(),
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

    public record CountResponse(
            Long countAll,
            Long countByOrder,
            Long countByCreatedAt
    ) {
        public static CountResponse from(Long countAll, Long countByOrder, Long countByCreatedAt) {
            return new CountResponse(countAll, countByOrder, countByCreatedAt);
        }
    }
}
