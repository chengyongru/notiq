# 0.1.0 device verification

**English** · [简体中文](verification.zh-CN.md)

For subsequent prompt changes and APK checks, see [prompt evaluation](prompt-evaluation.md). This document preserves the initial build results and the switch to the 4B service.

Date: 2026-09-23. Device: vivo V2454A, Android 16 / API 36.

APK SHA-256: `4d98e21974137ba7bc88828f4fed5c813d63870b7dba3d4116cbfe0c902371b6`.

## Results

| Check | Result | Evidence |
| --- | --- | --- |
| Debug build | PASS | `:app:assembleDebug` succeeded; installation and launch succeeded |
| Protocol and conservative handling tests | PASS | `:app:testDebugUnitTest`: 5 tests, 0 failures |
| Android Lint | PASS | `:app:lintDebug` succeeded; remaining suggestions concerned newer dependency versions |
| Observe flow | PASS | The separate fixture sent promotion and delivery notifications; the real FastJev service returned decisions, Room records appeared in the UI, and original notifications remained |
| Unavailable service | PASS | After removing ADB port forwarding, a new notification showed a connection-failure reason and remained in the system notification list |
| Feedback persistence | PASS | A promotion marked “Should filter” remained in the Reviewed list after an in-place installation |
| Light and dark themes | PASS | History, Settings and Rules checked; light-theme status-bar icons corrected; system theme restored |
| Listener after in-place installation | WARN | Permission remained granted but the service was unbound. The app’s reconnect request did not restore it. Revoking and granting Notiq access through the system notification command restored processing of new notifications |
| Model classification quality | WARN | Qwen3-0.6B Q8_0 assigned a high keep score to an obvious promotion under the full default rule. Feedback was recorded; no filtering accuracy claim was made |
| Official Jev API | NOT RUN | No official API key was used in this round |
| Automatic removal on device | NOT RUN | Observe mode remained enabled throughout this round |

## Initial local inference environment

Separate Python 3.12 environment: `D:\tools\notiq-fastjev-py312`. FastJev 0.1.1 used the llama.cpp backend with the llama-cpp-python 0.3.35 CPU wheel. The existing Qwen3-0.6B Q8_0 GGUF was reused at revision `23749fefcc72300e3a2ad315e1317431b06b590a`.

The server listened only on the computer’s `127.0.0.1:8000`; the phone connected through `adb reverse tcp:8000 tcp:8000`. Its initial served-model was `fastjev-local`. That process was replaced by the 4B server described below. Forwarding must be restored after the debugging connection is lost.

## Qwen3.5-4B API switch

Completed at 22:19 on 2026-09-23. The model ran on the computer’s RTX 4070 Ti. The phone only called FastJev’s `POST /v1/systemone`; no model was deployed to the phone.

- Environment: `D:\tools\notiq-fastjev-4b`, Python 3.12.10, FastJev 0.1.1, Torch 2.10.0+cu128 and Transformers 5.17.0. `uv pip check` passed; CUDA and BF16 were available.
- Model: `D:\models\Qwen3.5-4B-851bf6e`, Qwen3.5-4B BF16, revision `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`. SHA-256 values for both weight shards matched Hugging Face metadata.
- Server: computer loopback `127.0.0.1:8000`, served-model `fastjev-qwen3.5-4b`. The phone connected through ADB reverse; Test connection succeeded after saving settings. The old 0.6B server was stopped.
- Inference: direct option scoring through FastJev’s native Torch backend, without generated explanations. The full default Chinese rule and the `keep`, `filter`, `uncertain` option order were preserved.

The following synthetic API samples verified connectivity and a small number of behaviors. They do not measure accuracy or false-positive rates. Probabilities are uncalibrated option scores.

| Sample | Decision | Selected option probability | Local HTTP duration |
| --- | --- | --- | --- |
| Limited-time offer, claim coupons and buy now | keep; missed promotion | 69.28% | 1.05 s, first request |
| Order delivered | keep | 99.26% | 0.31 s |
| New-device login security alert | keep | 99.28% | 0.16 s |
| You have a new message; tap for details | keep | 84.51% | 0.16 s |

Both fixture notifications passed through the 4B API on the device. Details showed `fastjev / fastjev-qwen3.5-4b`, with scores matching direct API requests. The system notification list confirmed that originals remained. The promotion was marked “Should filter”. Observe mode remained enabled, only the fixture was selected, and automatic removal was disabled. Deployment and connectivity: PASS. Classification quality: WARN.

No APK changes or Android rebuilds were made in this round. The server was left running and test notifications were cleared. Evidence: `artifacts/settings-qwen4b.png`, `qwen4b-ad-detail.png`, `qwen4b-delivery-detail.png`, `qwen4b-ad-result.json`, and `qwen4b-samples-results.json`. Server logs: `artifacts/fastjev-4b-8000.*.log`.

## Evidence locations

Screenshots are local files in the ignored `artifacts/` directory: `home-final.png`, `home-light-final.png`, `settings-dark.png`, `rules-light.png`, and `fail-open.png`. Early light-theme screenshots may show the status bar before its correction; the final APK for this round included the fix.

JUnit XML: `app/build/test-results/testDebugUnitTest/`. Lint report: `app/build/reports/lint-results-debug.html`.

Only `dev.notiq.fixture` was selected. Observe mode, synthetic records and feedback were retained; everyday apps were not selected and no notifications were sent to the official cloud service in this round. The fixture remained installed, with its active notifications cleared at the end.
