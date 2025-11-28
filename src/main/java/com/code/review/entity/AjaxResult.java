package com.code.review.entity;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

@Data
public class AjaxResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "状态码")
    private int code;
    @ApiModelProperty(value = "返回信息")
    private String msg;
    @ApiModelProperty(value = "返回数据")
    private Object data;

    public AjaxResult() {
    }

    public AjaxResult(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static AjaxResult success(int code, String msg, Object data) {
        return new AjaxResult(code, msg, data);
    }

    public static AjaxResult success(String msg, Object data) {
        return success(HttpStatus.OK.value(), msg, data);
    }

    public static AjaxResult success(String msg) {
        return success(msg, null);
    }

    public static AjaxResult success(Object data) {
        return success("操作成功", data);
    }

    public static AjaxResult success() {
        return success(null);
    }

    public static AjaxResult error(int code, String msg, Object data) {
        return new AjaxResult(code, msg, data);
    }

    public static AjaxResult error(String msg, Object data) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), msg, data);
    }

    public static AjaxResult error(String msg) {
        return error(msg, null);
    }

    public static AjaxResult warn(int code, String msg, Object data) {
        return new AjaxResult(code, msg, data);
    }

    public static AjaxResult warn(String msg, Object data) {
        return warn(HttpStatus.BAD_REQUEST.value(), msg, data);
    }

    public static AjaxResult warn(String msg) {
        return warn(msg, null);
    }
}
