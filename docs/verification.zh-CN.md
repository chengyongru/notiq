# 0.1.0 真机验证

[English](verification.md) · **简体中文**

后续提示词优化及新版 APK 验证见 [提示词验证](prompt-evaluation.zh-CN.md)。以下保留初次构建和 4B 切换时的结果。

日期：2026-09-23。设备：vivo V2454A，Android 16 / API 36。

APK SHA-256：`4d98e21974137ba7bc88828f4fed5c813d63870b7dba3d4116cbfe0c902371b6`。

## 结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Debug 构建 | PASS | `:app:assembleDebug` 成功，安装并启动成功 |
| 协议及保守处理测试 | PASS | `:app:testDebugUnitTest`，5 个测试，0 失败 |
| Android Lint | PASS | `:app:lintDebug` 成功；剩余提示为依赖新版本建议 |
| 观察链路 | PASS | 独立 fixture 发出促销、物流通知；真实 FastJev 返回结果，Room 记录在界面显示，原通知仍存在 |
| 服务不可用 | PASS | 移除 ADB 端口转发后发送通知；详情显示“服务连接失败，保留通知”，系统仍列出该通知 |
| 反馈持久化 | PASS | 促销记录标记“应该过滤”，覆盖安装后“已反馈”列表仍可找到该记录 |
| 明暗主题 | PASS | 检查记录、设置、规则界面；修正浅色状态栏图标；恢复跟随系统 |
| 覆盖安装后的监听 | WARN | 权限仍在，但系统未绑定服务。应用请求重连未恢复；通过系统 notification 命令撤销并重新授予 Notiq 的权限后恢复，随后新通知处理成功 |
| 模型分类质量 | WARN | Qwen3-0.6B Q8_0 对完整默认规则下的一条明显促销通知给出高分“保留”；该样本已标注反馈，未据此宣称过滤准确率 |
| 官方 Jev 在线调用 | NOT RUN | 没有使用官方 API 密钥 |
| 真机自动移除 | NOT RUN | 本轮按观察阶段要求保持观察模式，没有开启自动移除 |

## 初次验证的本地推理环境

独立 Python 3.12 环境：`D:\tools\notiq-fastjev-py312`。使用 FastJev 0.1.1 的 llama.cpp 后端、llama-cpp-python 0.3.35 CPU wheel。复用 Qwen3-0.6B Q8_0 GGUF，修订 `23749fefcc72300e3a2ad315e1317431b06b590a`。

服务仅监听电脑 `127.0.0.1:8000`，通过 `adb reverse tcp:8000 tcp:8000` 给手机访问。初次验证 served-model 为 `fastjev-local`；该进程已被下面的 4B 服务替换。调试连接断开后需要重新转发。

## Qwen3.5-4B API 切换验证

2026-09-23 22:19 完成。模型运行在电脑的 RTX 4070 Ti 上，手机只调用 FastJev 的 `POST /v1/systemone`。没有向手机部署模型。

- 环境：`D:\tools\notiq-fastjev-4b`，Python 3.12.10、FastJev 0.1.1、Torch 2.10.0+cu128、Transformers 5.17.0。`uv pip check` 通过，CUDA 和 BF16 可用。
- 模型：`D:\models\Qwen3.5-4B-851bf6e`，Qwen3.5-4B BF16，修订 `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`。两个权重分片的 SHA-256 与 Hugging Face 元数据一致。
- 服务：电脑 `127.0.0.1:8000`，served-model `fastjev-qwen3.5-4b`。手机通过 ADB reverse 访问，设置保存后“测试连接”成功。原 0.6B 服务已停止。
- 推理方式：FastJev 原生 Torch 后端的直接选项评分，无生成式解释。保持完整默认中文规则及 `keep`、`filter`、`uncertain` 选项顺序。

API 合成样本结果如下。这里只验证接通与少量行为，不构成准确率或误删率评估；表中概率是未经校准的选项分数。

| 样本 | 返回 | 所选项概率 | 本机 HTTP 耗时 |
| --- | --- | --- | --- |
| 限时优惠、领券抢购 | keep，广告漏判 | 69.28% | 1.05 秒，首次请求 |
| 订单已送达 | keep | 99.26% | 0.31 秒 |
| 新设备登录安全提醒 | keep | 99.28% | 0.16 秒 |
| 您有一条新消息、点击查看详情 | keep | 84.51% | 0.16 秒 |

真机 fixture 促销和物流通知均经过 4B API，详情显示 `fastjev / fastjev-qwen3.5-4b`，分数与直接 API 请求一致。处理后通过系统通知列表确认原通知存在；促销记录标注“应该过滤”。保持观察模式，仅选择测试应用，未开启自动移除。部署接通 PASS，分类质量 WARN。

本轮未修改 APK，也未重新执行 Android 构建。服务继续运行，测试通知已清理。证据：`artifacts/settings-qwen4b.png`、`qwen4b-ad-detail.png`、`qwen4b-delivery-detail.png`、`qwen4b-ad-result.json`、`qwen4b-samples-results.json`；服务日志为 `artifacts/fastjev-4b-8000.*.log`。

## 证据位置

本机截图在忽略目录 `artifacts/`：`home-final.png`、`home-light-final.png`、`settings-dark.png`、`rules-light.png`、`fail-open.png`。早期浅色截图可能包含修正前的状态栏，最终 APK 已修正。

JUnit XML：`app/build/test-results/testDebugUnitTest/`。Lint 报告：`app/build/reports/lint-results-debug.html`。

仅选择了 `dev.notiq.fixture`。保留观察模式、合成通知记录及误判反馈；没有选择日常应用或向官方云服务发送通知。测试通知发送器保留安装，测试结束时清除其活跃通知。
