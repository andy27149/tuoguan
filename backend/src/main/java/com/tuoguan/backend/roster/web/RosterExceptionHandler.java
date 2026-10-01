package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.admin.web.InvalidLeaveDateException;
import com.tuoguan.backend.admin.web.InvalidFeatureFlagsException;
import com.tuoguan.backend.admin.web.InvalidLogoException;
import com.tuoguan.backend.admin.web.InvalidTeacherRoleException;
import com.tuoguan.backend.admin.web.InvalidTransferTargetException;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.CoursePriceNotConfiguredException;
import com.tuoguan.backend.course.web.DuplicateConsumptionException;
import com.tuoguan.backend.course.web.DuplicateCourseNameException;
import com.tuoguan.backend.course.web.RechargeNotAllowedException;
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

    @ExceptionHandler(DuplicatePhoneException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDuplicatePhone() {
    }

    @ExceptionHandler(InvalidTransferTargetException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidTransferTarget() {
    }

    @ExceptionHandler(InvalidTeacherRoleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidTeacherRole() {
    }

    @ExceptionHandler(BillingRateNotConfiguredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBillingRateNotConfigured() {
    }

    @ExceptionHandler(InvalidLeaveDateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidLeaveDate() {
    }

    @ExceptionHandler(InvalidLogoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidLogo() {
    }

    @ExceptionHandler(InvalidFeatureFlagsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidFeatureFlags() {
    }

    @ExceptionHandler(DuplicateCourseNameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDuplicateCourseName() {
    }

    @ExceptionHandler(DuplicateConsumptionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDuplicateConsumption() {
    }

    @ExceptionHandler(CourseNotEnrolledException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleCourseNotEnrolled() {
    }

    @ExceptionHandler(CoursePriceNotConfiguredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleCoursePriceNotConfigured() {
    }

    @ExceptionHandler(RechargeNotAllowedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleRechargeNotAllowed() {
    }
}
