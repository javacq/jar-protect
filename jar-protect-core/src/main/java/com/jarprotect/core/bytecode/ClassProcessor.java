package com.jarprotect.core.bytecode;

import org.objectweb.asm.*;

/**
 * 字节码处理器。
 * 使用 ASM 对 .class 文件进行方法体擦除：
 * - 保留类名、方法签名、注解信息（兼容 Spring/Swagger 反射）
 * - 清空方法体，替换为 throw RuntimeException
 */
public class ClassProcessor {

    private static final int ASM_API = Opcodes.ASM9;

    /**
     * 擦除类中所有方法体，替换为抛出异常。
     * 保留类结构、字段声明、方法签名和所有注解。
     *
     * @param originalBytecode 原始字节码
     * @return 擦除后的字节码
     */
    public static byte[] eraseMethodBodies(byte[] originalBytecode) {
        ClassReader reader = new ClassReader(originalBytecode);
        // 仅使用 COMPUTE_MAXS，不使用 COMPUTE_FRAMES
        // COMPUTE_FRAMES 需要在加密时加载所有引用类（BOOT-INF/lib 中的依赖），
        // 加密时 classpath 不完整会导致帧计算失败，生成错误的 class 文件
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new MethodBodyEraserVisitor(ASM_API, writer);
        // 使用 SKIP_FRAMES 因为我们会自己处理帧
        reader.accept(visitor, ClassReader.SKIP_FRAMES);

        return writer.toByteArray();
    }

    /**
     * 检查字节码是否为有效的 Java class 文件。
     */
    public static boolean isValidClass(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        // Java class 文件魔数: 0xCAFEBABE
        return (data[0] & 0xFF) == 0xCA
                && (data[1] & 0xFF) == 0xFE
                && (data[2] & 0xFF) == 0xBA
                && (data[3] & 0xFF) == 0xBE;
    }

    /**
     * 从字节码中提取类的全限定名。
     */
    public static String extractClassName(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        return reader.getClassName().replace('/', '.');
    }

    /**
     * 方法体擦除的 ClassVisitor 实现。
     */
    private static class MethodBodyEraserVisitor extends ClassVisitor {

        private String className;
        private String superName;

        MethodBodyEraserVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
            this.superName = superName;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            // 保留抽象方法、native方法和接口方法（它们没有方法体）
            if ((access & Opcodes.ACC_ABSTRACT) != 0 || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            // 对于静态初始化块 <clinit>，保留以维持类的初始化逻辑中的常量
            if ("<clinit>".equals(name)) {
                return mv;
            }

            return new MethodBodyReplacer(ASM_API, mv, access, descriptor, className, name, superName);
        }
    }

    /**
     * 将方法体替换为返回默认值（对 CGLIB/AOP 友好）。
     */
    private static class MethodBodyReplacer extends MethodVisitor {

        private final MethodVisitor target;
        private final int access;
        private final String descriptor;
        private final String className;
        private final String methodName;
        private final String superName;
        private boolean visitedCode = false;

        MethodBodyReplacer(int api, MethodVisitor mv, int access, String descriptor,
                           String className, String methodName, String superName) {
            // 传 null 给 super，这样不会转发原始的方法体指令
            super(api, null);
            this.target = mv;
            this.access = access;
            this.descriptor = descriptor;
            this.className = className;
            this.methodName = methodName;
            this.superName = superName;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            // 保留方法上的注解（如 @RequestMapping, @Override 等）
            return target.visitAnnotation(desc, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            // 保留参数注解（如 @RequestParam, @PathVariable 等）
            return target.visitParameterAnnotation(parameter, desc, visible);
        }

        @Override
        public void visitCode() {
            visitedCode = true;
            target.visitCode();

            if ("<init>".equals(methodName)) {
                // 构造器：调用 super() 然后 return
                target.visitVarInsn(Opcodes.ALOAD, 0);
                target.visitMethodInsn(Opcodes.INVOKESPECIAL, superName,
                        "<init>", "()V", false);
                target.visitInsn(Opcodes.RETURN);
                target.visitMaxs(1, 1);
                return;
            }

            // 普通方法：根据返回类型生成默认返回值
            Type returnType = Type.getReturnType(descriptor);
            switch (returnType.getSort()) {
                case Type.VOID:
                    target.visitInsn(Opcodes.RETURN);
                    break;
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT:
                    target.visitInsn(Opcodes.ICONST_0);
                    target.visitInsn(Opcodes.IRETURN);
                    break;
                case Type.LONG:
                    target.visitInsn(Opcodes.LCONST_0);
                    target.visitInsn(Opcodes.LRETURN);
                    break;
                case Type.FLOAT:
                    target.visitInsn(Opcodes.FCONST_0);
                    target.visitInsn(Opcodes.FRETURN);
                    break;
                case Type.DOUBLE:
                    target.visitInsn(Opcodes.DCONST_0);
                    target.visitInsn(Opcodes.DRETURN);
                    break;
                default: // OBJECT, ARRAY
                    target.visitInsn(Opcodes.ACONST_NULL);
                    target.visitInsn(Opcodes.ARETURN);
                    break;
            }
            target.visitMaxs(2, 1);
        }

        @Override
        public void visitEnd() {
            if (!visitedCode) {
                // 如果没有 code 属性（不应该到这里，但做防守处理）
                target.visitEnd();
                return;
            }
            target.visitEnd();
        }

        // 以下所有 visitXxx 指令方法不转发（方法体被擦除）
        @Override
        public void visitInsn(int opcode) { }

        @Override
        public void visitIntInsn(int opcode, int operand) { }

        @Override
        public void visitVarInsn(int opcode, int var) { }

        @Override
        public void visitTypeInsn(int opcode, String type) { }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) { }

        @Override
        public void visitJumpInsn(int opcode, Label label) { }

        @Override
        public void visitLabel(Label label) { }

        @Override
        public void visitLdcInsn(Object value) { }

        @Override
        public void visitIincInsn(int var, int increment) { }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) { }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature,
                                       Label start, Label end, int index) { }

        @Override
        public void visitLineNumber(int line, Label start) { }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) { }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { }
    }
}
