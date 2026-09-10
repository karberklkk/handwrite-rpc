package com.handwrite.rpc.api;

import java.io.Serializable;

public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    // 请求唯一标识(M2-2 协议头里携带);响应会回填同一个值,用于"哪个响应对应哪次调用"
    private long requestId;
    // 调哪个服务(建议存接口全限定名,如 com.handwrite.rpc.example.HelloService)
    private String serviceName;
    // 调哪个方法
    private String methodName;
    // 参数类型数组(反射定位方法必须,没有它重载会匹配错)
    private Class<?>[] parameterTypes;
    // 实参数组
    private Object[] parameters;

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

        public RpcRequest() {
    }

    public RpcRequest(String serviceName, String methodName, Class<?>[] parameterTypes, Object[] parameters) {
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.parameters = parameters;
    }
        public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    // ↓ 继续写 methodName / parameterTypes / parameters 的 get/set
        public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "RpcRequest{serviceName='" + serviceName + "', methodName='" + methodName + "'}";
    }
}