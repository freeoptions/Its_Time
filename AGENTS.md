# 到点啦项目开发规则

本文件补充全局开发规则，适用于本仓库。涉及提醒调度、后台触发和通知可靠性的修改，必须遵守以下约束。

## 已验证的提醒触发架构

2026-08-21 已在 Xiaomi 13（Android 13）真机验证以下两种场景均能准时触发：

- 从最近任务中划掉应用后保持亮屏；
- 从最近任务中划掉应用后锁屏静置。

当前可靠链路为：

1. 使用 `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ...)` 注册用户设定的精确提醒；
2. 使用清单静态注册的显式 `BroadcastReceiver` 接收闹钟，`PendingIntent` 必须使用稳定且互不冲突的 requestCode，并带 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`；
3. Receiver 一收到广播就先持久化 `ALARM_FIRED` 日志，并标记 `stage=RECEIVER`；
4. Receiver 随即启动短生命周期前台服务，由服务完成重复提醒续排、通知发布和诊断记录；
5. 服务完成后立即 `stopForeground(STOP_FOREGROUND_REMOVE)` 并 `stopSelf(startId)`，不得把它改造成常驻保活服务；
6. 如果短时前台服务启动失败，必须在 Receiver 内直接投递通知作为降级路径；
7. 重复提醒必须先续排下一次系统闹钟，再发布本次通知，避免通知层异常导致调度链断裂。

对应核心文件：

- `app/src/main/java/com/daodianla/app/ReminderScheduler.kt`
- `app/src/main/java/com/daodianla/app/ReminderAlarmReceiver.kt`
- `app/src/main/java/com/daodianla/app/ReminderTriggerService.kt`
- `app/src/main/AndroidManifest.xml`

## 权限与系统恢复

- Android 13 及以上提醒类核心功能使用 `USE_EXACT_ALARM`；兼容旧系统时保留项目现有的精确闹钟权限策略。
- 使用短生命周期前台服务时必须声明 `FOREGROUND_SERVICE`；Android 14 及以上使用 `shortService` 类型。
- 保留开机完成、系统时间变化、时区变化、应用升级和精确闹钟权限变化后的全量重新调度。
- 每次调度日志必须记录是否精确、采用的 AlarmManager API、来源、用户目标时间和系统提交时间。

## 禁止退化

- 不得把精确提醒主链路替换为 WorkManager、普通 `set()`、轮询、Handler 或应用内计时器。
- WorkManager 只能作为允许延迟的补偿方案，不能宣称其能准点触发。
- 不得为了可靠性增加永久显示的“提醒守护中”通知或常驻前台服务，除非用户以后明确改变要求。
- 不得把“从最近任务划掉”直接判定为“强行停止”。前者已经通过真机测试；设置页强行停止、清除数据和卸载仍属于无法由应用自行恢复的系统边界。
- 不得静默委托系统时钟创建真实响铃闹钟；如将来增加，只能作为用户明确开启的独立兜底选项。

## Shizuku 增强边界

如以后引入 Shizuku，只允许做用户可见、可撤销、按应用生效的后台限制增强，例如检查或设置：

- `RUN_IN_BACKGROUND`、`RUN_ANY_IN_BACKGROUND` AppOps；
- 应用待机分组为 `active`；
- 应用 inactive 状态为 `false`；
- 系统支持时将本应用后台限制设为 `unrestricted`。

不得复制会修改整台设备全局行为的方案，例如全局关闭 Phantom Process Killer。Shizuku 不得成为基础提醒功能的必选依赖。

## 真机回归流程

提醒可靠性变更必须覆盖以下人工测试；测试期间不得重新打开到点啦补发：

1. 设置未来 2 至 5 分钟的提醒，确认日志包含 `SET_EXACT_AND_ALLOW_WHILE_IDLE`；
2. 从最近任务划掉应用，保持亮屏静置，验证准时收到通知；
3. 再设置一条提醒，从最近任务划掉应用并锁屏静置，验证准时收到通知；
4. 打开诊断日志，确认顺序包含 `PROCESS_STARTED`、`ALARM_FIRED(stage=RECEIVER)`、短时服务接管和 `NOTIFICATION_POSTED`；
5. 验证重复提醒已经续排到下一次时间，且通知栏没有残留短时服务通知。

如果测试失败，必须先根据日志判断是系统闹钟未投递、Receiver 未进入、短时服务启动失败还是通知发布失败，再修改代码，禁止靠反复增加保活组件猜测修复。
