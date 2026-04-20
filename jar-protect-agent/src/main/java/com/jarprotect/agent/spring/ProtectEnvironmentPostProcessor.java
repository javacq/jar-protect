package com.jarprotect.agent.spring;

import com.jarprotect.agent.ProtectAgent;
import com.jarprotect.agent.ResourceDecryptor;

import java.io.InputStream;
import java.util.Properties;

/**
 * Spring Boot 环境后处理器集成（可选）。
 * 
 * 资源解密现在通过 ASM 注入到 ClassPathResource#getInputStream() 实现，
 * 此类保留作为手动解密资源的工具入口。
 */
public class ProtectEnvironmentPostProcessor {

    /**
     * 初始化资源解密（设置系统属性）。
     */
    public static void initialize() {
        if (!ProtectAgent.isInitialized()) return;
        String password = ProtectAgent.getDecryptPassword();
        if (password != null) {
            System.setProperty("jarprotect.decrypt.password", password);
        }
    }

    /**
     * 手动解密从 ClassLoader 获取的资源。
     */
    public static InputStream getDecryptedResource(ClassLoader classLoader, String resourcePath) {
        try {
            return ResourceDecryptor.tryDecrypt(resourcePath);
        } catch (Exception e) {
            System.err.println("[JAR-Protect] 解密资源失败: " + resourcePath);
            return null;
        }
    }

    /**
     * 解密 properties 文件内容。
     */
    public static Properties loadDecryptedProperties(ClassLoader classLoader, String resourcePath) {
        Properties props = new Properties();
        try {
            InputStream is = getDecryptedResource(classLoader, resourcePath);
            if (is != null) {
                props.load(is);
                is.close();
            }
        } catch (Exception e) {
            System.err.println("[JAR-Protect] 加载解密配置文件失败: " + resourcePath);
        }
        return props;
    }
}
