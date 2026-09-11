package com.example.eventapp.common;

public record CurrentUser(Long userId, String name, String role) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
