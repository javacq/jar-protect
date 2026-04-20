# JAR-Protect

**Java 应用 JAR 包加密保护平台 — 加密、机器码绑定、注册码授权、AES+RSA 混合加密**

基于字节码处理技术，对编译后的 `.class` 文件和配置文件进行 AES-256-GCM 加密，防止源码泄露和反编译。集成机器码绑定、注册码验证（支持 HMAC / RSA 签名两种模式，永久/限期授权），支持 AES+RSA 混合加密（自动生成密码，只有持有私钥才能解密）。加密后的 JAR 可直接 `java -jar` 运行，无需手动传递密码或 Agent 参数。

---

## 功能特性

- **字节码混淆与擦除** — ASM 清空方法体，保留类结构/注解/方法签名，兼容 Spring Boot/Swagger 反射
- **AES-256-GCM 加密** — PBKDF2 密钥派生（65536 迭代），高强度认证加密
- **AES+RSA 混合加密** — RSA-2048 保护 AES 密码，无需记忆密码，只有持有私钥才能解密/运行
- **RSA 密钥管理** — 内置 RSA 密钥对生成、查看、导出功能（GUI 独立 Tab 页）
- **资源文件加密** — 支持 `.yml`、`.properties`、`.xml` 等配置文件加密
- **依赖库保护** — 支持递归加密 `BOOT-INF/lib/` 下的私有依赖 JAR
- **机器码绑定** — 基于 MAC/CPU/主板/硬盘序列号的硬件指纹，支持 Windows/Linux/macOS
- **注册码授权** — 支持两种模式：
  - **HMAC 模式** — HMAC-SHA256 短码（XXXX-XXXX-XXXX-XXXX），传统密钥模式
  - **RSA 签名模式** — RSA 私钥签名，JAR 内嵌公钥验证，无法伪造注册码
- **自动嵌入引擎** — 加密时自动将解密 Agent 嵌入 JAR，直接 `java -jar` 运行
- **License 文件验证** — 客户只需在 JAR 同目录放置 `license.lic` 文本文件
- **GUI 管理工具** — Midnight Forge 深色主题，支持加密、解密、RSA 密钥、机器码、注册码全流程操作
- **Windows EXE 发行** — 自动生成 EXE + 精简 JRE，双击即可运行，无需安装 Java
- **客户端工具** — 独立 EXE/JAR，供最终用户查看机器码
- **虚拟化环境容错** — Docker/VMware 环境降级策略

## 项目结构

```
jar-protect/
├── jar-protect-core/       # 核心模块：加密/解密、机器码、注册码、RSA、字节码处理
├── jar-protect-agent/      # Java Agent + ProtectLauncher：运行时解密引擎
├── jar-protect-cli/        # 命令行工具 + GUI：加密、解密、RSA 密钥、注册码管理
├── jar-protect-client/     # 客户端工具：机器码查看（独立 EXE）
└── pom.xml                 # 父 POM
```

## 构建

```bash
# 要求 JDK 11+、Maven 3.6+
mvn clean install
```

构建产物：

| 文件 | 说明 |
|------|------|
| `jar-protect-cli/target/JAR-Protect.exe` | 加密工具 Windows EXE（双击打开 GUI） |
| `jar-protect-cli/target/JAR-Protect-windows.zip` | Windows 发行包（EXE + 精简 JRE + Agent JAR） |
| `jar-protect-cli/target/JAR-Protect-linux.tar.gz` | Linux 发行包（启动脚本 + JAR + Agent JAR） |
| `jar-protect-client/target/机器码获取工具.exe` | 客户端 Windows EXE |
| `jar-protect-client/target/机器码获取工具-windows.zip` | 客户端 Windows 发行包（EXE + 精简 JRE） |
| `jar-protect-client/target/机器码获取工具-linux.tar.gz` | 客户端 Linux 发行包 |
| `jar-protect-cli/target/jar-protect-cli-1.0.0.jar` | 加密工具 JAR（CLI + GUI） |
| `jar-protect-agent/target/jar-protect-agent-1.0.0.jar` | 运行时解密 Agent |

### 发行包使用

**Windows：** 解压 zip，双击 `JAR-Protect.exe` 即可运行（已内置精简 JRE，无需安装 Java）。

**Linux：** 解压 tar.gz，执行 `./jarprotect.sh`（需要系统安装 JRE 1.8+）。

> 发行包中已自动捆绑 `jar-protect-agent-*.jar`，加密面板会自动选中同目录的 Agent JAR。

---

## 快速开始

### 推荐方式：AES+RSA 混合加密 + RSA 注册码

这是最安全的使用方式：密码自动生成、RSA 保护密码、RSA 签名注册码，全程无需手动管理密码。

#### 第一步：服务商 — 生成 RSA 密钥对

在 GUI 的 **"RSA 密钥"** Tab 页：
1. 输入密钥种子（相同种子始终生成相同密钥对，留空则随机生成）
2. 点击生成，保存 `public.pem` 和 `private.pem`

#### 第二步：服务商 — 加密 JAR

在 GUI 的 **"加密 JAR"** Tab 页：
1. 选择输入 JAR 和加密包路径
2. 勾选 **"AES+RSA 混合加密"**，选择 `public.pem`（密码自动生成，无需输入）
3. 勾选 **"启用注册码验证"**
4. 点击 **"开始加密"**

或 CLI：

```bash
java -jar jar-protect-cli-1.0.0.jar encrypt \
  -i app.jar \
  -p com.example.** \
  --password mySecret \
  --agent-jar jar-protect-agent-1.0.0.jar \
  --license-required \
  --license-expiry 2026-12-31
```

#### 第三步：客户 — 获取机器码

客户运行客户端工具（双击 EXE 或运行 JAR）：

```bash
# Windows: 双击 机器码获取工具.exe
# Linux/JAR:
java -jar jar-protect-client-1.0.0.jar
```

#### 第四步：服务商 — 生成 RSA 注册码

在 GUI 的 **"注册码"** Tab 页：
1. 选择 **RSA 私钥签名模式**
2. 输入客户机器码
3. 选择 `private.pem`
4. 设置有效期
5. 点击 **"生成注册码"**
6. 生成的注册码格式为 `XXXX-XXXX-XXXX-XXXX`，与机器码长度一致

#### 第五步：客户 — 运行加密 JAR

1. 将 `private.pem` 放在 JAR 同目录（用于 RSA 解密 AES 密码）
2. 创建 `license.lic`，写入注册码
3. 直接运行：
   ```bash
   java -jar app-encrypted.jar
   ```

### 传统方式：纯 AES + HMAC 注册码

#### 加密

```bash
java -jar jar-protect-cli-1.0.0.jar encrypt \
  -i app.jar \
  -p com.example.** \
  --password mySecret \
  --agent-jar jar-protect-agent-1.0.0.jar \
  --license-required \
  --license-expiry 2026-12-31
```

参数说明：
- `--agent-jar` — 自动嵌入解密引擎到输出 JAR 中
- `--license-required` — 启用注册码验证
- `--license-expiry` — 注册码过期日期（`PERMANENT` 表示永久，默认永久）

#### 生成 HMAC 注册码

```bash
java -jar jar-protect-cli-1.0.0.jar license \
  --generate \
  -m A1B2-C3D4-E5F6-7890 \
  --password mySecret \
  --expiry 2026-12-31
# 输出: 注册码: F9E8-D7C6-B5A4-3210
```

> **注意：** `--expiry` 必须与加密时的 `--license-expiry` 一致，否则注册码无法匹配。

#### 客户运行

1. 在 JAR 同目录创建 `license.lic`，写入注册码
2. 直接运行：`java -jar app-encrypted.jar`

---

## 两种加密模式对比

| 特性 | 纯 AES 模式 | AES+RSA 混合模式 |
|------|-------------|-----------------|
| 加密密码 | 手动输入，需记忆 | 自动生成，RSA 加密保护 |
| 密码存储 | XOR 混淆存入 manifest | RSA 加密后存入 manifest |
| 解密方式 | 内置密码自动解密 | 需 `private.pem` 在 JAR 同目录 |
| 安全性 | 密码可能被逆向提取 | 无私钥则无法解密 |

## 两种注册码模式对比

| 特性 | HMAC 模式 | RSA 模式 |
|------|----------|--------|
| 格式 | `XXXX-XXXX-XXXX-XXXX` | `XXXX-XXXX-XXXX-XXXX` |
| 生成 | 需要共享密钥 | 用 RSA 私钥推导 HMAC 密钥 |
| 验证 | JAR 内嵌密钥或默认密钥 | 运行时需要 private.pem |
| 安全性 | 知道密钥可自行生成 | 只有持有私钥才能生成/验证 |

---

## CLI 命令参考

### encrypt — 加密 JAR

```bash
java -jar jar-protect-cli-1.0.0.jar encrypt [选项]
```

| 选项 | 说明 |
|------|------|
| `-i, --input` | 输入 JAR 文件路径 |
| `-o, --output` | 输出路径（默认: `原名-encrypted.jar`） |
| `-p, --packages` | 加密包路径模式，逗号分隔（如 `com.example.**`） |
| `--password` | 加密密码（AES+RSA 模式下自动生成） |
| `-m, --machine-code` | 绑定机器码（可选） |
| `--resource-ext` | 资源文件扩展名（默认: `.yml,.yaml,.properties,.xml`） |
| `--encrypt-libs` | 加密 `BOOT-INF/lib` 依赖 |
| `--lib-pattern` | Lib 文件名模式，逗号分隔 |
| `--agent-jar` | Agent JAR 路径（自动嵌入解密引擎） |
| `--license-required` | 启用注册码验证 |
| `--license-key` | 自定义验证密钥（可选） |
| `--license-expiry` | 过期时间（`yyyy-MM-dd` 或 `PERMANENT`） |

### license — 注册码管理

```bash
# 生成注册码（永久）
java -jar jar-protect-cli-1.0.0.jar license \
  --generate -m A1B2-C3D4-E5F6-7890 --password mySecret

# 生成限期注册码
java -jar jar-protect-cli-1.0.0.jar license \
  --generate -m A1B2-C3D4-E5F6-7890 --password mySecret \
  --expiry 2026-12-31

# 验证注册码
java -jar jar-protect-cli-1.0.0.jar license \
  --verify -l F9E8-D7C6-B5A4-3210 -m A1B2-C3D4-E5F6-7890 \
  --password mySecret --expiry 2026-12-31
```

| 选项 | 说明 |
|------|------|
| `-g, --generate` | 生成模式 |
| `-v, --verify` | 验证模式 |
| `-m, --machine-code` | 目标机器码（不指定则使用当前机器） |
| `-l, --license` | 注册码（验证时使用） |
| `--password` | 密钥 |
| `--expiry` | 过期时间（`yyyy-MM-dd` 或 `PERMANENT`，默认永久） |

### machinecode — 机器码

```bash
java -jar jar-protect-cli-1.0.0.jar machinecode          # 显示机器码
java -jar jar-protect-cli-1.0.0.jar machinecode --detail  # 显示详细硬件信息
```

---

## 传统方式：手动 Agent 运行

如果不嵌入 Agent（未指定 `--agent-jar`），仍可用传统方式运行：

```bash
# 通过 agent 参数传递密码
java -javaagent:jar-protect-agent-1.0.0.jar=password=mySecret \
  -jar app-encrypted.jar

# 通过环境变量（推荐生产环境）
export JAR_PROTECT_PASSWORD=mySecret
java -javaagent:jar-protect-agent-1.0.0.jar -jar app-encrypted.jar
```

---

## 加密原理

```
原始 JAR                              加密后 JAR（嵌入引擎模式）
├── com/example/                      ├── com/example/
│   ├── App.class (完整代码)           │   ├── App.class (空壳: 方法体擦除)
│   └── Service.class                 │   └── Service.class (空壳)
├── application.yml (明文)            ├── application.yml (AES 加密密文)
└── MANIFEST.MF                       ├── com/jarprotect/agent/
                                      │   ├── ProtectLauncher.class (新 Main-Class)
                                      │   ├── ProtectAgent.class
                                      │   └── DecryptTransformer.class
                                      ├── META-INF/
                                      │   ├── encrypted/
                                      │   │   ├── com/example/App.class.enc
                                      │   │   └── com/example/Service.class.enc
                                      │   └── protect-manifest.json
                                      └── MANIFEST.MF
                                          Main-Class: ProtectLauncher
                                          Launcher-Agent-Class: ProtectAgent
```

### 运行时解密流程（自动嵌入模式）

1. **`java -jar`** → JVM 加载 `ProtectLauncher`（新 Main-Class）
2. **获取 Instrumentation** → 通过 `Launcher-Agent-Class`（JDK 9+）或 self-attach（JDK 8）
3. **加载清单** → 读取 `protect-manifest.json`
4. **密码恢复** → 纯 AES 模式解混淆密码；AES+RSA 模式用私钥解密 AES 密码
5. **注册码验证** → 读 `license.lic`，自动识别 HMAC / RSA 格式并验证
6. **机器码校验** → 采集硬件指纹，与预置哈希比对
7. **注册 Transformer** → 拦截类加载，按需解密 `.enc` 字节码
8. **启动原始应用** → 反射调用原始 Main-Class 的 `main` 方法

### 注册码算法

**HMAC 模式：**
```
注册码 = HMAC-SHA256(机器码 + "|" + 过期时间, 密钥) → 取前 8 字节 → XXXX-XXXX-XXXX-XXXX
```

**RSA 模式：**
```
hmacKey = SHA256(privateKey.encoded)
注册码 = HMAC-SHA256(机器码 + "|" + 过期天数, hmacKey) → 取前 8 字节 → XXXX-XXXX-XXXX-XXXX
```

- 过期时间参与 HMAC 计算，**篡改日期会导致注册码不匹配**
- 过期时间编码在注册码内部（前 2 字节）
- RSA 模式的 HMAC 密钥由私钥推导，只有持有私钥才能生成/验证
- license.lic 只需存放注册码一行

### License 文件查找顺序

1. `<JAR 所在目录>/license.lic`
2. `~/.jarprotect/license.lic`
3. 环境变量 `JAR_PROTECT_LICENSE`

---

## 安全设计

| 特性 | 说明 |
|------|------|
| 加密算法 | AES-256-GCM（认证加密，防篡改） |
| 混合加密 | RSA-2048 加密 AES 密码（OAEP + SHA-256） |
| 密钥派生 | PBKDF2WithHmacSHA256, 65536 迭代 |
| 机器码哈希 | SHA-256 |
| 注册码 | HMAC-SHA256（RSA 模式用私钥推导 HMAC 密钥） |
| 密码存储 | 纯 AES: XOR 混淆；AES+RSA: RSA 加密 |
| 过期时间 | 内嵌于加密 JAR manifest / 注册码签名，不可篡改 |
| 内存安全 | 解密字节码仅存在于 JVM 内存，磁盘无明文 |

## 兼容性

| 项目 | 支持范围 |
|------|----------|
| JDK | 8 及以上（JDK 9+ 自动使用 Launcher-Agent-Class） |
| 框架 | Spring Boot、Spring Framework、Spring Cloud |
| 打包 | 标准 JAR、Spring Boot Fat JAR、WAR |
| 平台 | Windows（EXE）、Linux（脚本）、macOS |
| 虚拟化 | Docker、VMware（降级容错） |

## 注意事项

1. **私钥安全** — RSA 私钥是最高机密，泄露等于丧失全部保护，勿上传公共仓库
2. **备份原始 JAR** — 加密不可逆（不含原始字节码），请务必保留原始 JAR
3. **机器码变更** — 更换硬件后机器码会变化，需重新生成注册码
4. **过期时间一致性** — HMAC 模式下，加密时的 `--license-expiry` 必须与生成注册码时的 `--expiry` 一致
5. **性能影响** — 解密仅在类首次加载时执行一次，运行时性能影响可忽略
6. **调试模式** — 加密后的 JAR 无法正常调试（方法体已擦除），调试请使用原始 JAR
7. **AES+RSA 运行** — 客户运行加密 JAR 时需将 `private.pem` 放在 JAR 同目录
8. **License 文件** — 纯文本，仅一行注册码，HMAC 模式不含过期时间（已内嵌于加密包中）
