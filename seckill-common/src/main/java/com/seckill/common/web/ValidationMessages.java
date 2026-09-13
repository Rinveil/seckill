package com.seckill.common.web;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 将校验 / 参数异常转为可读中文提示。 */
public final class ValidationMessages {

    private ValidationMessages() {
    }

    public static String of(Throwable e) {
        if (e instanceof MethodArgumentNotValidException manve) {
            return ofBindingResult(manve.getBindingResult());
        }
        if (e instanceof BindException be) {
            return ofBindingResult(be.getBindingResult());
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体格式错误或字段类型不正确";
        }
        return "参数错误";
    }

    public static String ofBindingResult(BindingResult bindingResult) {
        if (bindingResult == null || !bindingResult.hasErrors()) {
            return "参数错误";
        }
        Set<String> messages = new LinkedHashSet<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            messages.add(resolveMessage(error));
        }
        for (ObjectError error : bindingResult.getGlobalErrors()) {
            if (error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
                messages.add(error.getDefaultMessage());
            }
        }
        if (messages.isEmpty()) {
            return "参数错误";
        }
        return messages.stream().collect(Collectors.joining("；"));
    }

    private static String resolveMessage(FieldError error) {
        if (error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
            return error.getDefaultMessage();
        }
        return error.getField() + "不合法";
    }
}
