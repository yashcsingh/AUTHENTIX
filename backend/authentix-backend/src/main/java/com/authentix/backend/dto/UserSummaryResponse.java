package com.authentix.backend.dto;

import com.authentix.backend.entity.User;

public class UserSummaryResponse {

    private String userCode;
    private String fullName;
    private String role;

    public UserSummaryResponse() {
    }

    public UserSummaryResponse(String userCode, String fullName, String role) {
        this.userCode = userCode;
        this.fullName = fullName;
        this.role = role;
    }

    public static UserSummaryResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getUserCode(),
                user.getFullName(),
                user.getRole()
        );
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
