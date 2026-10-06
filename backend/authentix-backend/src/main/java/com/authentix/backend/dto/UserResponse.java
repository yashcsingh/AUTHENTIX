package com.authentix.backend.dto;

import com.authentix.backend.entity.User;

public class UserResponse {

    private Long id;
    private String userCode;
    private String fullName;
    private String email;
    private String role;
    private String qrCodeValue;
    private Boolean active;

    public UserResponse() {
    }

    public UserResponse(Long id, String userCode, String fullName, String email, String role, String qrCodeValue, Boolean active) {
        this.id = id;
        this.userCode = userCode;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.qrCodeValue = qrCodeValue;
        this.active = active;
    }

    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUserCode(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getQrCodeValue(),
                user.getActive()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getQrCodeValue() {
        return qrCodeValue;
    }

    public void setQrCodeValue(String qrCodeValue) {
        this.qrCodeValue = qrCodeValue;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
