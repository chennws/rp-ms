package com.ruoyi.web.controller.pojo;


import com.ruoyi.web.controller.enums.ResponseType;
import lombok.Data;

import java.io.Serializable;

@Data
public class Response<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;

    private String message;

//    private String appId;

//    private String dataType;

    private T data;

    public Response() {
        super();
    }

    public Response(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public Response(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T>Response<T> success(String message, T data){
        ResponseType type = ResponseType.SUCCESS;
        return new Response<>(type.getCode(),message,data);
    }

    public static <T>Response<T> success(T data){
        ResponseType type = ResponseType.SUCCESS;
        return new Response<>(type.getCode(),type.getMessage(),data);
    }

    public static <T>Response<T> success(){
        ResponseType type = ResponseType.SUCCESS;
        return new Response<>(type.getCode(),type.getMessage(),null);
    }

    public static <T>Response<T> failed(String message, T data){
        ResponseType type = ResponseType.FAILED;
        return new Response<>(type.getCode(),message,data);
    }

    public static <T>Response<T> failed(String message){
        ResponseType type = ResponseType.FAILED;
        return new Response<>(type.getCode(),message);
    }

    public static <T>Response<T> failed(){
        ResponseType type = ResponseType.FAILED;
        return new Response<>(type.getCode(),type.getMessage());
    }

    public static <T>Response<T> failed(ResponseType type){
        return new Response<>(type.getCode(),type.getMessage());
    }

    public static <T>Response<T> failed(ResponseType type, T data){
        return new Response<>(type.getCode(),type.getMessage(), data);
    }
}