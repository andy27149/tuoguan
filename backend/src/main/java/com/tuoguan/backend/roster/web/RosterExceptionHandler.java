package com.tuoguan.backend.roster.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class RosterExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(InvalidAvatarException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidAvatar() {
    }

    @ExceptionHandler(DuplicateClassNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDuplicateClassName() {
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleMaxUploadSizeExceeded() {
    }
}
