package com.example.ordersystem.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    CUSTOMER("ROLE_CUSTOMER"),
    OPERATION("ROLE_OPERATION"),
    ADMIN("ROLE_ADMIN");

    private final String roleName;

    UserRole(String roleName) {
        this.roleName = roleName;
    }

    public static class Constants {
        public static final String CUSTOMER = "ROLE_CUSTOMER";
        public static final String OPERATION = "ROLE_OPERATION";
        public static final String ADMIN = "ROLE_ADMIN";
    }
}
