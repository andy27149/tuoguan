package com.tuoguan.backend.auth.web;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
