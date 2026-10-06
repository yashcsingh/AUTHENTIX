package com.authentix.backend.dto;

public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private String userCode;
    private String fullName;
    private String email;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String userCode, String fullName, String email, String role) {
        this.token = token;
        this.type = "Bearer";
        this.userCode = userCode;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
}
