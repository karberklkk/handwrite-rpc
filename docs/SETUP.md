# 开发环境设置手册

> 目的：让"换一台机器 / 你自己终端"也能一键跑起来。README 讲项目，本文件讲环境。

## 1. 必备软件（你机器现状）

| 软件 | 状态 | 说明 |
|---|---|---|
| JDK | ✅ 已装 26（`D:\jdk-26_windows-x64_bin\jdk-26.0.2.1`） | 编译目标 17，向下兼容 |
| Maven | ✅ 已装 3.9.16（`..\.tools\apache-maven-3.9.16`） | 见下方"加入 PATH" |
| Git | ✅ 已装 2.55 | 仓库已 `git init`（main） |
| IDE | 推荐 IntelliJ IDEA（Community 免费版即可） | 见第 3 节导入步骤 |

## 2. 把 Maven 加入 PATH（一次性，推荐）

让以后在任何终端都能直接敲 `mvn`：

1. 按 `Win`，搜索"编辑系统环境变量" → 环境变量
2. 在"用户变量"里找到 `Path` → 编辑 → 新建，粘贴：
   ```
   C:\Users\L8619\Desktop\work place\.tools\apache-maven-3.9.16\bin
   ```
3. 确定后**新开一个终端**，运行 `mvn -version` 验证

> 也可以更规范地装到 `C:\tools\` 之类无空格路径，但当前 `.tools` 位置完全够用。
> ⚠️ 注意：Maven 默认本地仓库是 `C:\Users\L8619\.m2\repository`（会自动创建）。
> 只有"AI 沙箱里代跑构建"才需要 `-Dmaven.repo.local=...\.tools\m2-repo` 这种参数，你本人不用管。

## 3. IntelliJ IDEA 导入项目（若用 IDEA）

1. `File → Open`，选择 `C:\Users\L8619\Desktop\work place\handwrite-rpc`
2. 等右下角提示 import Maven project，或手动：右键根 `pom.xml` → `Add as Maven Project`
3. `File → Settings → Build Tools → Maven`：
   - 检查 JDK for importer / Runner 选 JDK 26 或任意 17+ JDK
   - 若本机有其他 JDK，Runner JRE 选 17+ 即可
4. 右上角 Maven 面板能展开 5 个模块（parent/rpc-api/rpc-common/rpc-core/example）即成功

> 你机器实测装的是 **VS Code**（未装 IDEA），用第 3.5 节即可。

## 3.5 VS Code 开发 Java 多模块项目（你机器当前方案）

**需要装的扩展**（扩展市场搜名字即可）：
- `Extension Pack for Java`（微软官方合集，包含语言服务、调试器、Maven 支持、测试）
- 装完右下角提示 Reload / 等它自动下载 JDK 语言服务器

**打开与操作**：
```powershell
code "C:\Users\L8619\Desktop\work place\handwrite-rpc"   # 在 VS Code 打开项目
```
- 左侧 Explorer 展开各模块的 `src/main/java` 写代码
- 右侧或命令面板（`Ctrl+Shift+P` → `Maven: ...`）可执行 `clean package`
- 写 main 方法后，点方法上方的 **Run** 直接运行；或终端里 `mvn -pl example -am compile` 编译
- 首次打开会让 Java 扩展建索引，稍等即可；若报 `Java runtime not found`，在设置里把 Java 指向 `D:\jdk-26_windows-x64_bin\jdk-26.0.2.1`

**纯命令行跑构建（不依赖 IDE）：**
```powershell
cd "C:\Users\L8619\Desktop\work place\handwrite-rpc"
mvn clean package          # 全量构建
mvn -pl example -am package   # 只构建 example 及其依赖
```

## 4. 首次提交并推送到 GitHub（M0 收尾清单）

```powershell
cd "C:\Users\L8619\Desktop\work place\handwrite-rpc"

# 1) 配置 git 身份（换成你自己的！提交记录会显示它）
git config --local user.name "你的GitHub用户名"
git config --local user.email "你的GitHub邮箱"

# 2) 提交
git add .
git commit -m "chore: init maven multi-module skeleton"

# 3) 确认 SSH key 已注册到 GitHub：
#    GitHub → Settings → SSH and GPG keys
#    本机公钥指纹: SHA256:PlONC95hfiGG/PWlw+VoGtT3HaslHhNmLznNYGTqlQo
#    （公钥文件在 C:\Users\L8619\.ssh\id_ed25519.pub，打开复制内容即可）

# 4) GitHub 网页新建空仓库（不要勾选 README/LICENSE）名字也叫 handwrite-rpc
git branch -M main
git remote add origin git@github.com:你的用户名/handwrite-rpc.git
git push -u origin main
```

推不上去？常见原因与排查：
```powershell
ssh -T git@github.com   # 期望输出 Hi <用户名>! ...
# 若 Permission denied: 钥匙没注册，回第 3) 步
# 若 timeout/网络问题: 你机器直连 GitHub 已验证可用（TLS 握手通过）；检查是否被系统代理 127.0.0.1:7890 影响
```

## 5. 常见问题速查

| 现象 | 原因 / 解法 |
|---|---|
| `mvn` 不是内部或外部命令 | 没加 PATH（第 2 节）或终端没重开 |
| 编译报 `invalid source release` | 编译器 target 与 JDK 不符；本项目固定 `--release 17`，用 17+ 的 JDK 即可 |
| `--release` 相关或语法高亮飘红 | 等 IDEA 索引/导入完成；确认 JDK for importer |
| 构建下载很慢 | 首次全量下载依赖正常；后续可考虑配阿里云镜像（见下方） |
| Netty 在 JDK26 报模块警告 | 先跑起来；确有问题再把版本升到 4.2.17.Final（pom 顶部有注释） |

## 6. （可选）配置 Maven 国内镜像加速

在 `C:\Users\L8619\.m2\settings.xml`（没有就新建）写：
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```
> 仅在你直连 Maven Central 慢的时候配；本项目在沙箱里直连中央仓库已成功。
