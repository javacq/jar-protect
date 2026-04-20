package com.jarprotect.agent;

import com.jarprotect.core.crypto.AESCrypto;
import com.jarprotect.core.model.ProtectManifest;
import org.objectweb.asm.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类文件解密转换器。
 * 在类加载阶段拦截加密类，从 JAR 中读取加密数据，解密后返回原始字节码。
 * 解密后的字节码直接加载到 JVM 内存，磁盘上不留存明文文件。
 */
public class DecryptTransformer implements ClassFileTransformer {

    private static final String ENCRYPTED_PREFIX = "META-INF/encrypted/";

    private final ProtectManifest manifest;
    private final String password;

    /** 加密类名缓存（内部名称格式: com/example/MyClass） */
    private final Set<String> encryptedClassNames;

    /** 是否有加密资源（需要拦截 ClassPathResource） */
    private final boolean hasEncryptedResources;

    /** ClassPathResource 是否已经被 patch */
    private volatile boolean classPathResourcePatched = false;

    /** 已解密的类计数 */
    private int decryptedCount = 0;

    /** 解密缓存：避免同一个类被重复解密 */
    private final Map<String, byte[]> decryptCache = new ConcurrentHashMap<>();

    public DecryptTransformer(ProtectManifest manifest, String password) {
        this.manifest = manifest;
        this.password = password;

        // 预处理加密类名集合，转换为内部名称格式
        this.encryptedClassNames = new HashSet<>();
        for (String entry : manifest.getEncryptedClasses()) {
            String className = entry;
            if (className.startsWith("BOOT-INF/classes/")) {
                className = className.substring("BOOT-INF/classes/".length());
            }
            if (className.startsWith("WEB-INF/classes/")) {
                className = className.substring("WEB-INF/classes/".length());
            }
            if (className.endsWith(".class")) {
                className = className.substring(0, className.length() - 6);
            }
            encryptedClassNames.add(className);
        }

        this.hasEncryptedResources = manifest.getEncryptedResources() != null
                && !manifest.getEncryptedResources().isEmpty();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {

        if (className == null) return null;

        // 拦截 Spring ClassPathResource，注入资源解密逻辑（参考 ClassFinal）
        if (hasEncryptedResources && !classPathResourcePatched
                && "org/springframework/core/io/ClassPathResource".equals(className)) {
            try {
                byte[] patched = patchClassPathResource(classfileBuffer);
                if (patched != null) {
                    classPathResourcePatched = true;
                    System.out.println("[JAR-Protect] 已注入资源解密到 ClassPathResource#getInputStream");
                    return patched;
                }
            } catch (Exception e) {
                System.err.println("[JAR-Protect] ClassPathResource patch 失败: " + e.getMessage());
            }
        }

        if (!encryptedClassNames.contains(className)) {
            return null;
        }

        try {
            // 检查缓存
            byte[] cached = decryptCache.get(className);
            if (cached != null) {
                return cached;
            }

            // 查找加密数据
            byte[] encryptedData = loadEncryptedData(loader, className);
            if (encryptedData == null) {
                System.err.println("[JAR-Protect Agent] 警告: 找不到加密数据 - " + className);
                return null;
            }

            // 解密
            byte[] decryptedBytecode = AESCrypto.decrypt(encryptedData, password);

            // 放入缓存
            decryptCache.put(className, decryptedBytecode);

            decryptedCount++;
            if (decryptedCount % 20 == 0) {
                System.out.println("[JAR-Protect Agent] 已解密加载 " + decryptedCount + " 个类...");
            }

            return decryptedBytecode;

        } catch (Exception e) {
            System.err.println("[JAR-Protect Agent] 解密失败 [" + className + "]: " + e.getMessage());
            // 解密失败返回 null，使用擦除后的空壳类（会在调用方法时抛出异常）
            return null;
        }
    }

    /**
     * 从类路径加载加密数据。
     * 查找路径: META-INF/encrypted/{className}.class.enc
     */
    private byte[] loadEncryptedData(ClassLoader loader, String className) {
        // 构建多种可能的资源路径
        String[] possiblePaths = {
                ENCRYPTED_PREFIX + className + ".class.enc",
                ENCRYPTED_PREFIX + "BOOT-INF/classes/" + className + ".class.enc",
                ENCRYPTED_PREFIX + "WEB-INF/classes/" + className + ".class.enc"
        };

        for (String path : possiblePaths) {
            byte[] data = loadResource(loader, path);
            if (data != null) {
                return data;
            }
        }

        return null;
    }

    /**
     * 从类加载器中加载资源字节。
     */
    private byte[] loadResource(ClassLoader loader, String path) {
        try {
            InputStream is = null;
            if (loader != null) {
                is = loader.getResourceAsStream(path);
            }
            if (is == null) {
                is = ClassLoader.getSystemResourceAsStream(path);
            }
            if (is == null) {
                return null;
            }

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            } finally {
                is.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取已解密的类数量。
     */
    public int getDecryptedCount() {
        return decryptedCount;
    }

    /**
     * 使用 ASM 修改 ClassPathResource#getInputStream()，
     * 在方法开头注入 ResourceDecryptor.tryDecrypt(this.path) 调用。
     * 
     * 注入后的效果等同于：
     * <pre>
     * public InputStream getInputStream() throws IOException {
     *     InputStream __decrypted = ResourceDecryptor.tryDecrypt(this.path);
     *     if (__decrypted != null) return __decrypted;
     *     // ... 原始代码 ...
     * }
     * </pre>
     */
    private byte[] patchClassPathResource(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        // 使用 COMPUTE_FRAMES 让 ASM 重新计算所有 StackMapTable 帧，
        // 避免注入代码导致原有帧偏移失效 (VerifyError: bad offset)
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // 安全回退：避免因类加载器隔离导致 ClassNotFoundException
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                // 只修改 getInputStream()Ljava/io/InputStream;
                if ("getInputStream".equals(name) && "()Ljava/io/InputStream;".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();

                            // 注入: InputStream __d = ResourceDecryptor.tryDecrypt(this.path);
                            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                            mv.visitFieldInsn(Opcodes.GETFIELD,
                                    "org/springframework/core/io/ClassPathResource",
                                    "path", "Ljava/lang/String;");
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/jarprotect/agent/ResourceDecryptor",
                                    "tryDecrypt",
                                    "(Ljava/lang/String;)Ljava/io/InputStream;",
                                    false);

                            // if (result != null) return result;
                            mv.visitInsn(Opcodes.DUP);
                            Label continueLabel = new Label();
                            mv.visitJumpInsn(Opcodes.IFNULL, continueLabel);
                            mv.visitInsn(Opcodes.ARETURN);

                            // else: pop null, continue original code
                            mv.visitLabel(continueLabel);
                            mv.visitInsn(Opcodes.POP);
                        }
                    };
                }
                return mv;
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
