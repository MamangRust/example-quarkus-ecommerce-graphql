package com.example.entity.merchant;

import com.example.domain.requests.merchant.CreateMerchantRequest;
import com.example.domain.requests.merchant.UpdateMerchantRequest;
import com.example.enums.Status;
import com.example.entity.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(name = "merchants")
public class Merchant extends BaseModel {

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    public static Merchant fromCreateRequest(CreateMerchantRequest req) {
        Merchant merchant = new Merchant();
        merchant.setUserId(req.getUserId());
        merchant.setName(req.getName());
        merchant.setDescription(req.getDescription());
        merchant.setAddress(req.getAddress());
        merchant.setContactEmail(req.getContactEmail());
        merchant.setContactPhone(req.getContactPhone());

        try {
            merchant.setStatus(Status.valueOf(req.getStatus().toUpperCase()));
        } catch (IllegalArgumentException e) {
            merchant.setStatus(Status.PENDING);
        }

        return merchant;
    }

    public void updateFromRequest(UpdateMerchantRequest req) {
        this.userId = req.getUserId();
        this.name = req.getName();
        this.description = req.getDescription();
        this.address = req.getAddress();
        this.contactEmail = req.getContactEmail();
        this.contactPhone = req.getContactPhone();

        try {
            this.status = Status.valueOf(req.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            this.setStatus(Status.PENDING);
        }
    }
}