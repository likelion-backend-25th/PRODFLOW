package net.likelion.bebc25.prodflow.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {
    @Column(length = 10, nullable = false)
    private String zipcode;

    @Column(length = 100, nullable = false)
    private String roadAddress;

    @Column(length = 100, nullable = false)
    private String detailAddress;

    public Address(String zipcode, String roadAddress, String detailAddress) {
        this.detailAddress = detailAddress;
        this.zipcode = zipcode;
        this.roadAddress = roadAddress;
    }
}
