package com.tuoguan.backend.course.web;

public class RechargeNotAllowedException extends RuntimeException {

    public RechargeNotAllowedException(String message) {
        super(message);
    }
}
