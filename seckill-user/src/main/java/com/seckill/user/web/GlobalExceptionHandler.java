package com.seckill.user.web;

import com.seckill.common.exception.BusinessException;
import com.seckill.common.result.Result;
import com.seckill.common.result.ResultCode;
import com.seckill.common.web.ValidationMessages;
import io.jsonwebtoken.JwtException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleValidation(Exception e) {
        return Result.fail(ResultCode.BAD_REQUEST.code(), ValidationMessages.of(e));
    }

    @ExceptionHandler(JwtException.class)
    public Result<Void> handleJwt(JwtException e) {
        return Result.fail(ResultCode.UNAUTHORIZED.code(), ResultCode.UNAUTHORIZED.message());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.fail(500, "内部错误");
    }
}
