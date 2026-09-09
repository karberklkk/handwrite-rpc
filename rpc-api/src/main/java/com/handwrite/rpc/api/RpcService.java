package com.handwrite.rpc.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在服务提供方(Provider)的实现类上,
 * 声明"这个实现对外提供哪个接口的服务"。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RpcService {

    /**
     * 对外提供的服务接口,例如 HelloService.class
     */
    Class<?> value();
}
