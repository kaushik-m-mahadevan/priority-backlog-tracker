package com.backlogtracker.auth.dto;

public record LoginResponse(String token, UserView user) {
}
