package com.example.ordersystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class Address {
    @Column(name = "address_title", length = 100)
    private String title;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Column(name = "address_detail", length = 500)
    private String addressDetail;

    public Address(String title, String city, String district,
                   String zipCode, String country, String addressLine, String addressDetail) {
        this.title = title;
        this.city = city;
        this.district = district;
        this.zipCode = zipCode;
        this.country = country;
        this.addressLine = addressLine;
        this.addressDetail = addressDetail;
    }
}
