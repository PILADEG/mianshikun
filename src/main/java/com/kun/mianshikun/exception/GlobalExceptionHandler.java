package com.kun.mianshikun.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"系统错误");
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("JSON 解析失败：{}", e.getMessage());
        Throwable cause = e.getCause();
        if (cause instanceof InvalidFormatException) {
            InvalidFormatException invalidFormat = (InvalidFormatException) cause;
            String fieldName = invalidFormat.getPath().get(0).getFieldName();
            Object value = invalidFormat.getValue();
            String errorMsg = String.format("字段 [%s] 格式错误，值：%s", fieldName, value);
            log.info("字段格式错误 - 字段：{}, 值：{}, 错误：{}", fieldName, value, errorMsg);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, errorMsg);
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数错误");
    }
}
