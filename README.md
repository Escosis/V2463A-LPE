- # V2463A LPE - 基于 GhostLock 的单设备临时 ROOT 工具

  本项目是一个基于 CVE-2026-43499（GhostLock / 幽灵锁）漏洞的单设备临时 ROOT 方案，面向部分 vivo / iQOO 设备。

  > p.s. 应用命名中，V2463A 为本人的设备型号，也是最初该应用面向的设备型号，LPE 意为“本地提权”。所以尽管该项目内置了其他 `preload.so` ，对于其他型号其他内核版本的设备都不能保证一定兼容，需要自行测试，若失败可向我反馈。

  与目前市面上其他基于幽灵锁的单设备 ROOT 方案不同，本项目实现了完全在设备开机后、用户未解锁的锁屏界面下全自动完成提权，自动化程度与成功率都有显著提高。

  ## 为何要在解锁前执行

  漏洞利用的成功率与系统当前运行的内存环境密切相关。目前已有的基于幽灵锁的单机提权方案，通常需要用户在解锁后进入桌面手动触发。设备解锁后，大量系统服务和第三方应用被唤醒，复杂的进程环境极容易导致内存布局变化，从而使漏洞利用失败。

  本项目采用锁屏下全自动执行的方案。在设备开机并停留在锁屏界面（`LOCKED_BOOT_COMPLETED`）时，系统内部运行的进程极少，环境处于最纯净的状态。因此，本方案的提权成功率极高，达到了与“双设备（电脑 / 另一台手机 + 手机）联机提权”相同的稳定水平。

  ## 提权流程

  ### 第一阶段：payload 注入

  1. 设备重启后停留在锁屏界面，不进行解锁。
  2. 系统在锁屏状态下触发广播，拉起本应用并显示界面。
  3. 应用自动将选定的 `preload.so` 推送到 `/data/local/tmp/`，并自动连接本地无线调试。
  4. 通过本地 ADB shell 执行注入命令：
     ```bash
     LD_PRELOAD=/data/local/tmp/preload.so /system/bin/id
     ```
  5. 实时解析 `preload.so` 的输出：
     - **成功**：日志中出现 `root cred patched` 或 `embedded su daemon ready`，应用写入第一阶段标志。
     - **失败**：首次出现 `cfi miss`，代表 CFI 校验未通过、当前内存布局已偏移，应用立即调用 `reboot` 重启设备，回到第 1 步重新尝试。

  ### 第一阶段结束判定
  
  部分 `preload.so` 会在成功后自动软重启，其他则不会。shell 输出出现 `[结束]` 后，说明并未自动重启，应用稍作等待后判断：
  
  - **第一阶段成功标志存在**：**不重新连接 ADB**，原地切换为第二阶段，直接复用当前连接执行 `late-load`。
  - **第一阶段成功标志不存在**：调用 `adb reboot` 重启设备。

  ### 第二阶段：late-load 持久化

  1. 连接无线调试后，按 `su` → `/data/local/tmp/su` → `/apex/com.android.virt/bin/su` 的顺序尝试执行：
     ```bash
     su -c '.../libksud.so late-load --allow-shell --package-name <管理器包名>'
     ```
     输出含 `su: connect daemon: Connection refused` 时换下一个路径重试。
  2. `late-load` 输出含 `SO_NOT_FOUND`、`CHMOD_FAILED` 或 `LATE_EXIT` 非 0 时视为失败。
  3. 等待 `/system/bin/su` 返回 `uid=0`（30 × 500ms）。
  4. 修复 AVF su（详见下文「假权限的成因与修复」）。
  5. 执行 `su -c id` 验证，未返回 `uid=0` 视为失败。

  ### 自动软重启

  设置页「提权完成自动软重启」开启时，在第二阶段 `su` 验证通过后自动执行：

  ```bash
  su -c 'ksud soft-reboot'
  ```

  执行前先写入 `soft_reboot_pending.flag`（DE 存储），软重启后 `BootReceiver` 检测到该标志即删除并直接返回，**不再弹出锁屏提权面板**。若命令本身抛出异常（未真正触发软重启），标志会被回删，避免误拦截下一次真实重启。

  ### 第二阶段失败处理

  第二阶段任一环节失败（`su` 路径全部不可用、`late-load` 失败、`su` 就绪超时、验证失败）时，**不关闭无线调试、不关闭界面**，将「连接并提权」与「解锁」两个按钮恢复为可点击状态，方便用户直接重试。

  ## preload.so 来源选择

  即使内核版本完全匹配，也不保证一定能成功提权：**设备型号与内核版本均匹配**时成功率最高；**仅内核版本匹配**时大部分设备可以成功，但存在失败可能；**内核不匹配**时提权将失败。

  应用支持三种 `preload.so` 来源，在配对页「授权并推送」卡片中配置：

  - **自动检测**：读取当前内核版本（`os.version`）与设备型号（`ro.product.model`），在 `assets/<内核版本>/<型号>.so` 中匹配。内核版本必须**完全相等**，同内核下优先匹配型号，无型号匹配时回退到同内核的第一个其他型号。
  - **自定义文件**：通过系统文件选择器导入任意外部 `preload.so`，文件会拷贝到应用 **DE 存储**（`files/custom_preload.so`），锁屏提权阶段可直接读取，不依赖 SAF URI 的生命周期。大小限制 20MB。
  - **内置版本**：手动选择 `assets` 中的具体条目。

  **assets 目录约定**：

  ```text
  assets/
    6.6.89-android15-8-g1f71897ac249-.../
      V2463A.so          ← iQOO Neo10 Pro+
  ```

  - **目录名** = 完整内核版本（`os.version`）
  - **文件名** = `ro.product.model` + `.so`

  ### 对 `preload.so` 的逆向修改

  在部分`preload.so` 中，提权成功后会自动执行将系统壁纸修改为“Nebula Security”的逻辑。本项目对其进行了逆向修改，拦截了壁纸修改函数，使其直接返回状态码 `1` 而不执行操作。经过验证，此修改对提权无影响。

  ## 免解锁自连 ADB 原理
  
  为了让提权流程在锁屏且未解密用户数据的情况下完全自动化，本应用在 Android 系统框架层面绕过了多项限制：
  
  - **内置 `libadb.so`**：应用在 `jniLibs` 中内置了官方 ADB 二进制（重命名为 `libadb.so` 以取得可执行权限），锁屏状态下无需依赖其他应用的服务，即可完成无线调试的配对、连接与命令执行。
  - **Direct Boot 模式**：应用声明了 `android:directBootAware="true"` 并监听 `LOCKED_BOOT_COMPLETED` 广播，确保在用户输入密码解锁前，应用进程能够被系统拉起。
  - **自动开启无线调试**：在锁屏状态下，应用利用预授权的 `WRITE_SECURE_SETTINGS` 权限，直接向 `Settings.Global` 写入 `adb_wifi_enabled=1`，强行触发系统的无线调试服务。
  - **mDNS 自动发现**：应用在后台启动本地 mDNS 扫描，精准捕获系统 `adbd` 广播的 `_adb-tls-connect._tcp` 服务端口，并通过 `127.0.0.1` 建立回环连接。
  - **实际端口校验**：mDNS 广播的端口号可能在 TLS 授权完成前就已产生，或在授权后因 adbd 重启而变化。应用在 `adb connect` 之后会从 `adb devices` 输出中解析**实际在线的** `127.0.0.1:<port>`，并以此为准，避免保存无效端口导致后续命令报 `device not found`。
  - **DE 存储保护密钥**：由于未解锁时 CE（Credential Encrypted）存储不可读，应用将自连所需的 ADB 密钥强制存储在 DE（Device Encrypted）存储中，确保锁屏下 `libadb` 依然能正常读取并完成验证。

  ## 多管理器兼容

  不同分支的管理器应用包名与路径不同。应用在设置页允许用户选择预置的管理器分支，或输入自定义包名：

  - **KernelSU**（`me.weishu.kernelsu`）
  - **KernelSU Next**（`vctsrt.cntgtj.uqfwgg`）
  - **SukiSU-Ultra**（`com.sukisu.ultra`）
  - **BakaSU (ReSukiSU)**（`com.resukisu.resukisu`）
  - **自定义包名**

  在提权阶段，应用会根据用户填写的包名，在 `/data/app` 目录下直接查找并匹配对应的 `libksud.so` 路径后执行加载，从而兼容 KernelSU 生态的各衍生分支。

  预置分支在权限页「管理器授权」中支持**一键跳转到管理器应用**，方便用户授予 ROOT 权限及启用传统 SU 命令支持。自定义包名无法直接跳转，需要在权限页提示中手动从桌面启动管理器。

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

  通过将调用重定向至 `/system/bin/su`，解决了系统查找 `su` 时命中 AVF 错误二进制的问题。

  ## 致谢
  - CVE-2026-43499 漏洞发现与原始 PoC 贡献者
  - UI 框架：Compose Multiplatform / Miuix
  - ROOT 管理器：KernelSU 及其分支
  - preload.so 及 fix-avf-su.sh 提供者