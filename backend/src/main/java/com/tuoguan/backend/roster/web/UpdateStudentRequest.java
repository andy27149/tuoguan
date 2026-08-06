package com.tuoguan.backend.roster.web;

public record UpdateStudentRequest(String name, String schoolClassName, boolean enrolled) {
}
