package com.backlogtracker.commons.auth.dto;

public record LoginResponse(String token, UserView user) {
}
