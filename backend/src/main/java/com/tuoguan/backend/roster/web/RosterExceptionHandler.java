package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.admin.web.InvalidFeatureFlagsException;
import com.tuoguan.backend.admin.web.InvalidLogoException;
import com.tuoguan.backend.admin.web.InvalidTeacherRoleException;
import com.tuoguan.backend.admin.web.InvalidTransferTargetException;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.CoursePriceNotConfiguredException;
import com.tuoguan.backend.course.web.DuplicateConsumptionException;
import com.tuoguan.backend.course.web.DuplicateCourseNameException;
import com.tuoguan.backend.kanban.web.AlreadyArrivedException;
import com.tuoguan.backend.kanban.web.ArrivalRequiredException;
import com.tuoguan.backend.unit.web.FeatureDisabledException;
import com.tuoguan.backend.unit.web.InvalidTeachingUnitRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

// 名字叫 RosterExceptionHandler 是历史遗留——这其实是全仓库唯一一个 @RestControllerAdvice，
// 处理的异常横跨 admin/course/kanban/unit 等多个业务域，不只是 roster。没有改名是为了不在
// 这次改动里顺带做无关的重命名，功能上它就是应用级别的统一异常处理入口。
// 产品诊断 #66：继承 ResponseEntityExceptionHandler 而不是裸写一个新的
// @RestControllerAdvice 来兜底未知异常——Spring 跨多个 @ControllerAdvice bean 解析异常时，
// 是"第一个 bean 里只要有任意匹配（哪怕是 Exception.class 这种兜底）就直接返回"，不会
// 跨 bean 比较出最具体的那个；如果把兜底方法放进另一个独立的 advice 类，一旦它恰好在扫描
// 顺序上排在这个类前面，就会把下面这些更具体的 NotFoundException 等处理直接"吞掉"，这是
// 实测踩过的真实 bug。继承同一个基类、所有 @ExceptionHandler 方法都在同一个 bean 里，
// Spring 在单个 bean 内部按异常类型精确匹配最具体的方法，不会有这个问题；
// ResponseEntityExceptionHandler 本身还内置了 MethodArgumentNotValidException 等
// 20 多种 Spring MVC 标准异常的正确处理，同理不会被下面新加的 Exception.class 兜底覆盖。
@RestControllerAdvice
public class RosterExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RosterExceptionHandler.class);

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

    // MaxUploadSizeExceededException 不能再用 @ExceptionHandler 单独声明——继承
    // ResponseEntityExceptionHandler 之后，这个异常类型已经在父类内置的 handleException()
    // 里有精确匹配（同样是 depth 0），两个方法会打架变成 "Ambiguous @ExceptionHandler
    // method" 启动期错误（实测踩过）。改成重写父类暴露的 protected 钩子方法，保留原来
    // 一模一样的行为（400 + 空响应体）。
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
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

    @ExceptionHandler(FeatureDisabledException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleFeatureDisabled() {
    }

    @ExceptionHandler(InvalidTeachingUnitRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidTeachingUnitRequest() {
    }

    @ExceptionHandler(ArrivalRequiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleArrivalRequired() {
    }

    @ExceptionHandler(AlreadyArrivedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleAlreadyArrived() {
    }

    // @PreAuthorize 鉴权失败抛出的 AccessDeniedException 必须排除在兜底之外——它本来
    // 应该一路冒泡给 Spring Security 的 ExceptionTranslationFilter 处理成 403，不排除
    // 的话会被下面的 Exception.class 兜底先接住变成 500（实测踩过）。这里不处理，
    // 原样抛出去，让它逃出 DispatcherServlet 的异常解析链，回到 Security 的 filter。
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    // 产品诊断 #66：此前全仓库没有应用级兜底，任何没被上面这些具体类型捕获的异常会
    // 直接裸奔到 Spring 默认的空白错误响应，叠加诊断 #56（全仓库零业务日志）完全没有
    // 留痕，排查只能翻容器 stdout 里的裸 stacktrace。这里是最后一道防线：记日志 +
    // 统一返回不泄露内部细节的 500。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception while processing {}", request.getDescription(false), ex);
        return handleExceptionInternal(ex, Map.of("error", "服务器内部错误，请稍后重试"),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
