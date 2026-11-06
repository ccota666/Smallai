# 智能体模式技术实现详解

## 一句话概括
在手机上通过 Termux 运行 Linux 环境，调用 MiMoCode CLI（本地 AI 智能体），实时执行用户任务，并把过程和结果展示在聊天界面里。

## 整体架构

```
用户输入任务
    ↓
MiMoChat App（Android）
    ↓ 发送命令
Termux（终端模拟器）
    ↓ proot 环境
Ubuntu Linux（手机上的 Linux）
    ↓ 调用
MiMoCode CLI（mimo run）
    ↓ 连接
MiMo API（大模型）
    ↓ 返回结果
任务卡实时展示 + 产物文件
```

## 关键技术点

### 1. Termux 桥接（TermuxBridge）
MiMoChat 通过 Android 的 `RUN_COMMAND` 广播向 Termux 发送 shell 命令。Termux 里运行 proot-distro 提供的 Ubuntu Linux 环境，在其中执行 `mimo run` 命令。

```kotlin
// 向 Termux 发送命令
val intent = Intent("com.termux.RUN_COMMAND").apply {
    setClassName("com.termux", "com.termux.app.RunCommandService")
    putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
}
```

### 2. MiMoCode CLI 调用（MimoRunner）
生成 bash 脚本，base64 编码后写入共享目录，再通过 proot 执行：

```bash
cd /sdcard/Download/MiMoChat/conv_<id>   # 每对话独立工作目录
mimo run --yolo --model xiaomi/mimo-v2.6-pro '任务描述'
```

参数说明：
- `--yolo`：自动批准所有操作（无需人工确认）
- `--model`：指定模型（provider/model 格式）
- 工作目录：通过 `--dir` 参数指定

### 3. 记忆隔离（MIMOCODE_HOME）
每个对话有独立的 `MIMOCODE_HOME`，记忆和会话互不干扰：

```bash
export MIMOCODE_HOME=/root/.mimohome/conv_<id>
# config/state symlink 到全局（共享 API key 和模型配置）
ln -sfn /root/.config/mimocode $MIMOCODE_HOME/config
ln -sfn /root/.local/state/mimocode $MIMOCODE_HOME/state
# data/memory 独立（记忆隔离）
```

### 4. 实时日志展示（AgentTaskManager）
轮询 `mimo_run.log` 文件，把日志实时推送到聊天界面：

- 启动后每 2.5 秒读取一次日志文件
- `ANSI [0m]` 标记转为 `<SEG>` 分段
- 日志按规则分类：
  - `[0m]` 后跟自然语言 → **大字提示语**（Markdown 渲染）
  - `[0m$` 后跟命令 → **折叠显示**
  - `[0m→` / `[0m←` → **工具调用**（独立折叠）
  - 最后一段到 EXIT_CODE → **总结**（大字 Markdown）

### 5. 产物收捞
任务完成后扫描工作目录，把 AI 生成的文件收集到对话的产物列表：

```bash
find /root /tmp /sdcard/Download/MiMoChat/conv_<id>   -type f -mmin -20 | grep -E '\.(md|txt|docx|pptx|xlsx|pdf)$'
```

### 6. 文件上传
用户上传的附件复制到 `uploads/` 子目录（与 AI 产物区分），任务描述中引用：

```
参考文件（已在 uploads/ 目录）：uploads/report.pdf
```

## 数据流

```
用户消息 ──→ [任务卡消息插入数据库]
         ──→ MimoRunner.buildScript() 生成 bash
         ──→ base64 写入共享目录
         ──→ TermuxBridge 发送到 Termux
         ──→ proot Ubuntu 执行 mimo run
         ──→ AgentTaskManager 轮询日志
         ──→ 实时更新任务卡内容
         ──→ 完成后收集产物 + 更新附件
```

## 为什么这样设计？

| 选择 | 原因 |
|---|---|
| Termux 而非内置 | Android 不允许 App 直接执行 shell 命令 |
| proot Ubuntu | MiMoCode 需要 Linux 环境（Node.js 等） |
| 日志轮询 | mimo run 在后台执行，无法 WebSocket 推送 |
| 每对话独立目录 | 产物不混淆，记忆不串台 |
| `--yolo` | 手机上无人值守，需要全自动 |
| base64 传脚本 | 避免 shell 引号转义问题 |

## 依赖
- **Termux**（F-Droid 版）+ RUN_COMMAND 权限
- **proot-distro** + Ubuntu
- **MiMoCode CLI**（mimo run）
- **MiMo API**（大模型服务）
