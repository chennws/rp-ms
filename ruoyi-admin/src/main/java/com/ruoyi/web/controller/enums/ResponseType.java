package com.ruoyi.web.controller.enums;

import lombok.Getter;

@Getter
public enum ResponseType {
    FAILED("0", "failed"),
    SUCCESS("200", "success"),
    NOT_FOUND("404", "资源不存在"),
    NOT_SUPPORTED("405","不支持的请求"),
    SERVER_ERROR("500", "未知的异常"),
    PARAMS_ERROR("400", "参数异常"),
    UNAUTHORIZED("401", "访问未授权"),
    NOT_READABLE("400","请求体不存在"),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    NOT_LOGIN("NOT_LOGIN", "未登录"),
    UNREAD_APPID("UNREAD_APPID", "无法获取appId"),
    SECRET_ERROR("SECRET_ERROR", "密钥错误");

    private final String code;
    private final String message;

    ResponseType(String code, String message){
        this.code = code;
        this.message = message;
    }
}
