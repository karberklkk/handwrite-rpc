package com.handwrite.rpc.api;

import java.io.Serializable;

public class RpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // 对应请求的 requestId(Provider 必须原样回填,Consumer 靠它匹配响应)
    private long requestId;
    // 成功时的返回值(远程方法执行的返回结果)
    private Object data;
    // 失败时的错误信息(不能吞异常!调用方要知道服务端出了什么问题)
    private String errorMessage;

    // 判断是否成功:errorMessage == null 即成功
    public boolean isSuccess() {
        return errorMessage == null;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    // 无参构造 + 全参构造 + get/set + toString
        public RpcResponse() {
    }

    public RpcResponse(Object data, String errorMessage) {
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "RpcResponse{data=" + data + ", errorMessage='" + errorMessage + "'}";
    }
}
