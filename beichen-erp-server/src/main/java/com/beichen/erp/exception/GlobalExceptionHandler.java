package com.beichen.erp.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.beichen.erp.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return R.fail(401, "未登录");
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRoleException(NotRoleException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return R.fail(403, "无权限访问");
    }

    /**
     * F3-3（2026-09-18 接口级权限专项）：{@code @SaCheckPermission} 校验失败（缺页面级接口权限码）。
     * <p>此前该类异常未被覆盖 ⇒ 会掉进兜底变成 500「系统异常」，前端无法区分"没权限"与"服务出错"
     * （越权探测也拿不到清晰的拒绝信号）；现与 {@link NotRoleException} 口径一致返回 403。</p>
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("无接口权限: {}", e.getMessage());
        return R.fail(403, "无权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(400, msg);
    }

    /** 请求方法不匹配（如用 POST 调 GET 接口）：应返回 405，而不是被兜底包成"系统异常"500 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMethod());
        return R.fail(405, "请求方法不支持: " + e.getMethod());
    }

    /** 接口路径不存在：返回 404，而不是 500 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public R<Void> handleNotFound(Exception e) {
        log.warn("接口不存在: {}", e.getMessage());
        return R.fail(404, "接口不存在");
    }

    /**
     * 请求体不可读（日期格式非法、JSON 结构错误等）：P2-32。
     * <p>此前会被兜底成 500「系统异常:」（消息为空），前端无从判断；如 orderDate 传 "not-a-date"。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        String detail = rootMessage(e);
        log.warn("请求体解析失败: {}", detail);
        return R.fail(400, "请求参数格式不正确" + (detail.isBlank() ? "" : "：" + detail));
    }

    /**
     * 日期/数字解析失败（业务代码里 `LocalDate.parse` / `Long.valueOf` 等自行解析的入参）：P2-32。
     * <p>这类异常由业务代码抛出（不经过 Jackson），需要单独兜底，否则同样是 500「系统异常:」且消息为空。</p>
     */
    @ExceptionHandler({java.time.format.DateTimeParseException.class, NumberFormatException.class})
    public R<Void> handleParseError(RuntimeException e) {
        log.warn("入参解析失败: {}", e.getMessage());
        return R.fail(400, "请求参数格式不正确：" + e.getMessage());
    }

    /** 查询参数类型不匹配（如 pageSize=abc）：应 400，而不是 500（P2-32） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return R.fail(400, "参数「" + e.getName() + "」格式不正确：" + e.getValue());
    }

    /**
     * 数据完整性异常（字段超长 / 非空列缺值 / 唯一键冲突）：DB 层兜底，返回 400 可读提示（P2-32）。
     * <p>此前统一冒泡成 500「系统异常:」（消息为空），例如 remark 超长、资金账户缺名称、编码重复。</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        String detail = rootMessage(e);
        log.warn("数据完整性异常: {}", detail);
        String tip = "数据不符合约束";
        if (detail.contains("Data too long")) tip = "字段内容过长，请缩短后重试";
        else if (detail.contains("doesn't have a default value")) tip = "存在必填字段未填写";
        else if (detail.contains("Duplicate entry")) tip = "存在重复数据（编码/单号已被占用）";
        return R.fail(400, tip + "：" + detail);
    }

    /** 取异常链最底层的消息（DB 的报错原因藏在 cause 链里），并压平空白 */
    private String rootMessage(Throwable e) {
        Throwable t = e;
        String msg = e.getMessage() == null ? "" : e.getMessage();
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
            if (t.getMessage() != null) msg = t.getMessage();
        }
        return msg.replaceAll("\\s+", " ").trim();
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统异常: " + e.getMessage());
    }
}
