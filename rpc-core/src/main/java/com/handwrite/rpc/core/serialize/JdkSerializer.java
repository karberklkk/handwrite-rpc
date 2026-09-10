package com.handwrite.rpc.core.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * JDK 原生序列化实现(M2-2 先用它把对象变成字节)。
 *
 * 说明:JDK 序列化的缺点是体积大、性能一般、有反序列化安全风险;
 * M5 会抽出 Serializer 接口并支持 Kryo 等可插拔方案,这里先做最朴素的一版。
 */
public class JdkSerializer {

    /**
     * 对象 → 字节数组
     */
    public byte[] serialize(Object obj) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
            objectOut.writeObject(obj);
            objectOut.flush();
        } catch (IOException e) {
            throw new RuntimeException("JDK 序列化失败: " + e.getMessage(), e);
        }
        return byteOut.toByteArray();
    }

    /**
     * 字节数组 → 对象
     */
    public Object deserialize(byte[] bytes) {
        try (ObjectInputStream objectIn = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return objectIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("JDK 反序列化失败: " + e.getMessage(), e);
        }
    }
}
