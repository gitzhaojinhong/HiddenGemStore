package com.hiddengemstore.entity.dto;

import com.hiddengemstore.enums.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结果封装
 * @author : ZhaoJH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    /**
     * 请求处理结果标识
     * 用于指示业务操作是否成功执行
     * true表示成功，false表示失败
     */
    private Boolean success;

    /**
     * 错误提示信息
     * 当success为false时，提供具体的错误描述信息
     * 便于前端展示和用户理解失败原因
     */
    private String errorMsg;

    /**
     * 响应数据载体
     * 泛型设计以支持不同类型的业务数据返回
     * 成功时承载业务数据，失败时可能承载错误详情
     */
    private T data;

    /**
     * 数据总数（分页场景）
     * 用于列表查询等需要返回总记录数的场景
     * 配合data中的列表数据实现分页功能
     */
    private Long total;

    public static <T> Result<T> ok(){
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        return result;
    }
    public static <T> Result<T> ok(T data){
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }
    public static <T> Result<T> fail(){
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setErrorMsg("系统错误，请稍后重试!");
        return result;
    }
    public static <T> Result<T> fail(String errorMsg){
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        return result;
    }
    public static <T> Result<T> fail(T data){
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setData(data);
        return result;
    }
    public static <T> Result<T> fail(BaseCode baseCode){
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setErrorMsg(baseCode.getMsg());
        return result;
    }
}
