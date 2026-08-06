package com.tuoguan.backend.auth.web;

public record LoginResponse(String token, boolean mustChangePassword) {
}
