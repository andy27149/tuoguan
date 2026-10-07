package com.tuoguan.backend.kanban.web;

public class AlreadyArrivedException extends RuntimeException {

    public AlreadyArrivedException(String message) {
        super(message);
    }
}
