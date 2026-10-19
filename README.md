# V2463A LPE - 基于 GhostLock 的单设备免解锁 ROOT 方案

本项目是一个基于 CVE-2026-43499（GhostLock / 幽灵锁）漏洞的单设备临时 ROOT 方案，仅面向 iQOO Neo10 Pro+ 及相同芯片（骁龙8e）相同系统版本（OriginOS 6）设备。

与目前市面上其他基于幽灵锁的单设备 ROOT 方案不同，本项目实现了完全在设备开机后、用户未解锁的锁屏界面下全自动完成提权，自动化程度与成功率都有显著提高。


## 为何要在解锁前执行

漏洞利用的成功率与系统当前运行的内存环境密切相关。目前已有的基于幽灵锁的单机提权方案，通常需要用户在解锁后进入桌面手动触发。设备解锁后，大量系统服务和第三方应用被唤醒，复杂的进程环境极容易导致内存布局变化，从而使漏洞利用失败。

本项目采用锁屏下全自动执行的方案。在设备开机并停留在锁屏界面（`LOCKED_BOOT_COMPLETED`）时，系统内部运行的进程极少，环境处于最纯净的状态。因此，本方案的提权成功率极高，达到了与“双设备（电脑 / 另一台手机 + 手机）联机提权”相同的稳定水平。


## 提权流程

1. 设备重启后停留在锁屏界面，不进行解锁。
2. 系统在锁屏状态下触发广播，拉起本应用并显示界面。
3. 应用自动将内置的 `preload.so` 推送到 `/data/local/tmp/`，并自动连接本地无线调试。
4. 通过本地 ADB shell 执行注入命令：
   ```bash
   LD_PRELOAD=/data/local/tmp/preload.so /system/bin/id
   ```
5. 利用成功后，由 `preload.so` 触发系统自动重启；出现失败标志时应用自动调用重启，返回第 1 步。
6. 重启后，应用再次自动连接本地 ADB，调用目标管理器的 `libksud.so` 执行 `late-load`，完成内核级 ROOT 挂载。


## 免解锁自连 ADB 原理

为了让提权流程在锁屏且未解密用户数据的情况下完全自动化，本应用在 Android 系统框架层面绕过了多项限制：

- **Direct Boot 模式**：应用声明了 `android:directBootAware="true"` 并监听 `LOCKED_BOOT_COMPLETED` 广播，确保在用户输入密码解锁前，应用进程能够被系统拉起。
- **自动开启无线调试**：在锁屏状态下，应用利用预授权的 `WRITE_SECURE_SETTINGS` 权限，直接向 `Settings.Global` 写入 `adb_wifi_enabled=1`，强行触发系统的无线调试服务。
- **mDNS 自动发现**：应用在后台启动本地 mDNS 扫描，精准捕获系统 `adbd` 广播的 `_adb-tls-connect._tcp` 服务端口，并通过 `127.0.0.1` 建立回环连接。
- **DE 存储保护密钥**：由于未解锁时 CE（Credential Encrypted）存储不可读，应用将自连所需的 ADB 密钥强制存储在 DE（Device Encrypted）存储中，确保锁屏下 `libadb` 依然能正常读取并完成验证。


## 对 `preload.so` 的逆向修改

在原始的 GhostLock `preload.so` 中，提权成功后会自动执行将系统壁纸修改为“Nebula Security”的逻辑。本项目使用的 `preload.so` 经过了逆向汇编级修改：直接拦截并移除了该壁纸修改函数（使其返回状态码 `1` 而不执行操作）。经过验证，这一修改对提权核心机制没有任何影响，仅仅是为了保持设备原有壁纸不被篡改。


## 漏洞利用成功与失败的判断与处理

在注入 `preload.so` 后，其输出日志会清晰地指示漏洞利用的走向。系统（应用层）通过实时解析这些日志来决定下一步动作：

- **成功特征**：日志中打印 `[*] pselect returned attempt=1 ret=9 errno=0 calls=1 success=1`，随后会继续执行到 `[*] root cred patched` 等阶段。此时代表漏洞成功命中内存布局。此时将建立一个标志，下次重启检测到此标志则自动进入提权第二步。
- **失败特征与重启判断点**：如果在执行中出现 `[*] pselect cfi miss attempt=1/24 step=4 errno=0`，即**第一次出现 `cfi miss`**。此时代表 CFI（控制流完整性）校验未通过，当前环境内存布局已发生偏移，继续重试无意义。应用在监听到这一行输出的瞬间，会主动触发系统 `reboot` 重启设备，再次进入锁屏状态重新尝试。


## 多管理器兼容

不同分支的管理器应用包名与路径不同。应用在设置页允许用户自定义管理器包名。在提权阶段，应用会根据用户填写的包名，在 `/data/app` 目录下直接查找并匹配对应的 libksud.so 路径后执行加载，从而兼容 KernelSU Next 及 SukiSU-Ultra 等衍生分支。


## 假权限的成因与修复

在 KernelSU 的 `late-load` 模式下，系统的虚拟化框架（AVF）自带了一个 `su` 二进制（位于 `/apex/com.android.virt/bin/su`），其 `PATH` 优先级高于 KernelSU 生成的真 su（`/system/bin/su`）。这会导致执行 `su` 时被拦截，出现假权限。

**修复机制**：
应用在加载完内核级 ROOT 并确认 `/system/bin/su` 就绪后，会通过 ADB 执行以下命令，将原版 AVF su 替换为一个 shell Wrapper：

```shell
rm -f /apex/com.android.virt/bin/su
echo '#!/system/bin/sh' > /apex/com.android.virt/bin/su
echo 'exec /system/bin/su "$@"' >> /apex/com.android.virt/bin/su
chmod 755 /apex/com.android.virt/bin/su
```

通过将调用重定向至 `/system/bin/su`，完美解决了系统查找 `su` 时命中 AVF 错误二进制的问题。


## 致谢
- CVE-2026-43499 漏洞发现与原始 PoC 贡献者
- UI 框架：Compose Multiplatform / Miuix
- ROOT 管理器：KernelSU 及其分支
- preload.so 及 fix-avf-su.sh 贡献者