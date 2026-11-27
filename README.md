# 腕上翻页 · BandPad

![platform](https://img.shields.io/badge/手环-小米手环%209%20Pro-5DCAA5)
![platform](https://img.shields.io/badge/手表-REDMI%20Watch%206-5DCAA5)
![platform](https://img.shields.io/badge/手机-Android%207.0%2B-3DDC84)
![band](https://img.shields.io/badge/手环端-Vela%20快应用%20(RPK)-blue)
![license](https://img.shields.io/badge/license-MIT-5DCAA5)
![用途](https://img.shields.io/badge/用途-自用工具-lightgrey)

**在手表上滑一下，手机就翻一页；按住不动，抖音自动 2 倍速。** 刷视频、看电子书时不用再去够手机。

手表端是一个 Vela 快应用（`.rpk`），**用 [AstroBox](https://astrobox.online/) 刷入手环/手表**；手机端是一个用了无障碍手势的安卓 App。两端通过小米官方的 `interconnect` 通道通信。

**一个手机 App 通吃多个机型**，手表端按机型分别出包（当前提供小米手环 9 Pro 与 REDMI Watch 6），App 里手动选当前在用哪台即可。

```
手环 RPK ──滑动 / 长按 / 连点──▶ interconnect（BLE → 小米运动健康 → App）──▶ 手机 APK ──dispatchGesture()──▶ 翻页 / 加速 / 点赞收藏
```

---

## 适配与限制

| 项目 | 情况 |
|---|---|
| 手环 | 小米手环 9 Pro（`designWidth=168`，对应 336×480）。手环 9 / 9 NFC 等同分辨率 Vela 设备理论上可用，未实测 |
| 手表 | REDMI Watch 6（432×514，`designWidth=216`，HyperOS 3 / Vela 3.6+）。同一份源码，构建时换一套 manifest 与平台描述 |
| 其他 Vela 设备 | 复制一份 `manifest.<id>.json` + `platform.<id>.js` 就能出包，见「多机型」一节 |
| 手机 | Android 7.0+（`minSdk 23`），已在 Redmi K90 Pro Max / HyperOS 上使用 |
| 手环刷入方式 | **[AstroBox](https://astrobox.online/)**（也可用表盘自定义工具等支持 RPK 的工具） |
| 延迟 | 百毫秒级。这是「滑一下翻一页」的离散模式，不是逐帧跟手的触控板 |
| 已知无效场景 | 少数 App 自绘手势、屏蔽无障碍手势注入，这类翻不动，属系统限制 |

---

## 两种用法

**只想用，不打算编译** —— 直接装 `dist/` 里现成的两个包（也可以从 [Releases](https://github.com/fangzhengc/bandpad/releases/latest) 下载）。它们是同一次签名出来的，装完即可连通：

| 文件 | 装到哪 | 应用列表里显示的名字 |
|---|---|---|
| `dist/bandpad.apk` | 手机（所有机型共用这一个） | 腕上翻页 |
| `dist/com.bandpad.pageflip.release.1.2.1.9pro.rpk` | 小米手环 9 Pro（AstroBox 刷入） | 手环 9 Pro |
| `dist/com.bandpad.pageflip.release.1.2.1.watch6.rpk` | REDMI Watch 6（AstroBox 刷入） | Watch 6 |

> 仓库只放**当前这一个版本**：`1.2.1 / versionCode 12`，两端同版本号、同签名。旧版本的安装包不再随仓库分发。
>
> 两个 RPK **包名和签名完全相同**，只是界面基准宽度和图标不同：9 Pro 版 `designWidth=168`，Watch 6 版 `designWidth=216`。装错机型不会崩，但字会偏大或偏小。两个包的**应用名也不同**（见上表），装完看名字就知道装的是哪版。

**想改代码** —— clone 下来就能编，小米穿戴 SDK 已随仓库提供，不用自己申请：

```bash
git clone https://github.com/fangzhengc/bandpad.git
cd bandpad
bash scripts/gen-pem.sh    # 生成你自己的签名，必需，见下文
bash scripts/build.sh      # 一次出 RPK + APK，产物在 dist/
```

> ⚠️ 自己编译出来的包和 `dist/` 里那套**签名不同，不能混装**。要么两端都用现成的，要么两端都换成你自己编的。

---

## 安装

### 1. 手机端（先装这个）

装 `dist/bandpad.apk`，然后：

1. 打开「腕上翻页」→ 点 **① 打开无障碍设置** → 找到「腕上翻页」→ 打开
2. 回 App，顶部显示「无障碍服务：已开启」即成功
3. 点 **② 测试 下一页**：在任意界面随手指上滑一下 —— **这一步不依赖手环**，先确认无障碍能力可用，能排掉 90% 的问题
4. 点 **⑧ 测试长按加速**：它会替你按住屏幕左边缘 1.5 秒再松开。前台开着抖音时点，画面应出现 2 倍速；没反应说明当前 App 不支持长按加速，不是权限问题
5. 点 **⑨ / ⑩ 测试向左滑 / 向右滑**、**⑪ / ⑫ 测试双击 / 三击**：分别对应手环的横滑、双击点赞、三击收藏
6. 点 **⑬ 测试锁定 2 倍速** / **⑭ 测试取消锁定**：对应手环上「按住后下滑锁定 / 上滑取消」
7. HyperOS / MIUI 上再补三件事，否则息屏后会被杀：
   - 应用信息 → 省电策略 → **无限制**（或直接点 App 里 **⑥ 关闭电池优化**）
   - 应用信息 → 自启动 → **允许**（或点 **⑦ 打开自启动设置**）
   - 多任务界面把「腕上翻页」卡片**下拉锁定**

### 2. 手表端（用 AstroBox 刷入，按机型选包）

手环没有应用商店，快应用 RPK 必须侧载。本项目使用 **AstroBox** 刷入。

[AstroBox](https://astrobox.online/) 是面向小米穿戴设备（Xiaomi Vela / HyperOS）的第三方管理工具，支持安装 `.rpk` 快应用、`.bin` 表盘与固件，并可连接设备、传文件、管插件。

**准备**：手机装 AstroBox，把对应机型的 RPK 存到手机任意目录（下载目录即可）：

- 小米手环 9 Pro → `dist/com.bandpad.pageflip.release.1.2.1.9pro.rpk`
- REDMI Watch 6 → `dist/com.bandpad.pageflip.release.1.2.1.watch6.rpk`

**连接手环**（已连过可跳过）：

1. 打开 AstroBox → 用**小米账号**登录（自动拉取 AuthKey）；也可以手动输入 AuthKey
2. 手环上：设置 → 系统 → 连接新设备，屏幕会出现配对码
3. AstroBox → **设备** → 选中扫描到的手环 → 输入配对码 / AuthKey → 发送
4. 两端都点「允许配对」，提示配对成功即完成

**刷入快应用**：

1. AstroBox → 底部 **设备**
2. 点 **安装应用** → 选择 **本地文件**
3. 找到对应机型的 `.rpk`，加入任务队列
4. 点 **一键安装**，等 5～15 秒，手环上会弹出安装进度条
5. 装完手表应用列表里出现应用图标，**名字就是你的设备型号**（手环 9 Pro 版显示「手环 9 Pro」，Watch 6 版显示「Watch 6」），两台设备都装了也不会混

> **升级覆盖安装**：新 RPK 的 `versionCode` 必须比已装的大，否则手环不会更替。本项目手表端当前为 `1.2.1 / versionCode 12`，旧版本可直接覆盖升级。

> AstroBox 只是「把文件送进手环」的工具，不改变应用行为。装了 AstroBox 不必保持它常驻，日常翻页完全不需要它。

<details>
<summary>其他刷入方式（备选，本项目未使用）</summary>

- **表盘自定义工具**：连接设备 → 「安装快应用」→ 选 RPK
- **adb**：手机开 USB 调试，`adb push xxx.rpk /sdcard/Download/`，再用文件管理器点开、选「小米运动健康」打开安装

</details>

### 3. 给手环授权（漏了这步必然报 `permission denied`）

官方 interconnect 的顺序是「找设备 → 查安装 → **申请权限** → 注册监听」，缺一不可。

1. 手环端装好后，回手机 App 点 **④ 重新连接手环**
2. 日志出现「发现设备：小米手环9 Pro」+「手环端应用已安装」后，点 **⑤ 申请手环权限**
3. 手环上若弹确认，点允许；成功后日志出现「授权完成」+「消息通道已就绪」
4. 状态栏「手环通道」变成 **已连通，可以滑手环了** 就成功了

> 权限只需给一次，之后 App 重启会自动 `checkPermissions` 复用。已连通时 ⑤ 会变灰 —— SDK 对同一台手环只允许注册一次，重复点会被拒。

### 4. 使用

手环上打开「腕上翻页」：

- 顶部绿点 = 手机通道已连上；红点 = 没连上（回手机 App 点 ④）
- **上滑 → 下一页**，**下滑 → 上一页**，一次一页，带震动反馈
- **左滑 / 右滑 → 手机上横滑一下**：不是翻页，而是真的横向滑动手势（相册翻图、左右切换的列表、看小说的左右翻章都吃这个）
- **双击 → 点赞**：在画面中央双击，走抖音原生的双击点赞
- **三击 → 收藏**：不是靠连点，而是找界面上的「收藏」按钮点它（抖音没有三击收藏这个手势）；找不到按钮节点时按屏幕右侧坐标兜底
- **按住不动 → 2 倍速**：手环上按满 0.42 秒开始，手机在**屏幕左侧边缘**持续按住（抖音的长按加速只认两侧约 1/5 宽的区域，按正中间会弹倍速浮层），松手即恢复
- **加速中下滑 → 锁定 2 倍速**：松手也不再恢复，界面显示 `2× 已锁定`，适合整段视频一直看
- **锁定后上滑 → 取消锁定**：抬起手机上的手指，回到 1 倍速；锁定时手环上的翻页/横滑会被忽略，避免误操作
- **（Watch 6）底部还有一排按钮**：`▲ 上一页` / `▼ 下一页` / `2×`。手表的上下滑经常被系统手势（回表盘、通知中心）抢走，滑不动时点按钮；`2×` 是点一下锁定加速、再点一下取消
- **退出只走右上角按钮**，四个方向的滑动都不再触发退出，避免刷视频时误退
- 手机 App 的「运行记录」能看到每条指令及执行结果

---

## 多机型：怎么切换、怎么加新机型

**手机端切换**：App 顶部有个「目标设备：xxx（点击切换）」，选当前在用的那台。选择存在本地，重启仍生效。它决定手势的几何参数（长按落点、横滑与翻页幅度）以及日志里的标注——目前 9 Pro 与 Watch 6 的参数一致，切换主要是给以后屏幕比例不同的设备留位置。

**加一个新机型**（以手环 10 为例，四步）：

1. 复制 `band/manifest.9pro.json` → `band/manifest.band10.json`，改 `designWidth`（屏宽 ÷ 2）、`icon`、版本号
2. 复制 `band/src/common/platform.watch6.js` → `band/src/common/platform.band10.js`，改 `id` / `name` / `pad`（屏幕小、手势够用的机型写 `pad: false`）
3. 在 `scripts/build.sh` 的 `BAND_PROFILES` 里加上 `band10`
4. 手机端在 `DeviceProfile.ALL` 里加一条（不加也能用，会走默认参数）

```bash
bash scripts/build.sh --profile watch6   # 只出某一个机型
bash scripts/build.sh --band-only        # 只出手表包（所有机型）
bash scripts/build.sh --phone-only       # 只出 APK
```

---

## 从源码编译

不需要 Android Studio，全程命令行。`scripts/build.sh` 会一起出两端产物。

### 1. 环境

| 需要 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 编译安卓端 |
| Android SDK | platform-34 + build-tools 34.0.0 | 用 `cmdline-tools` 的 `sdkmanager` 装 |
| Gradle | 8.5+ | 或直接用 `gradlew` |
| Node.js | 18+ | 手环端 `aiot-toolkit` 需要 |

```bash
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-34" "build-tools;34.0.0"
cd band && npm install
```

需要设两个环境变量：`JAVA_HOME` 和 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）。`scripts/build.sh` 会自动探测它们，也会生成 `phone/local.properties` 里的 `sdk.dir`，不用手填。

> 仓库不含 `.toolchain/`（作者本机的免安装 JDK+SDK+Gradle，约 2.4G）和 `node_modules/`。前者是可选的加速包——放在仓库根目录下，`build.sh` 会优先用它；没有就走系统环境。
>
> **小米穿戴 SDK（`phone/app/libs/xms-wearable-lib_1.4_release.aar`）已随仓库提供**，所以不用走官方申请流程就能编译。它的版权归小米，使用限制见 [NOTICE.md](NOTICE.md)。

### 2. 生成签名（**必须做，仓库不含私钥**）

interconnect 要求 **RPK 与 APK 同包名 + 同签名**。仓库出于安全考虑不含任何私钥，请生成自己的一套：

```bash
bash scripts/gen-pem.sh                        # 密码自动随机生成并打印出来
BANDPAD_PASS=你的密码 bash scripts/gen-pem.sh    # 想用固定密码
BANDPAD_PASS=旧密码 bash scripts/gen-pem.sh --force  # 丢弃旧 keystore 重建
```

它做三件事：

1. 生成 `phone/keystore/pageflip.p12`（安卓 keystore）
2. 导出 `band/sign/{debug,release}/{private,certificate}.pem`（快应用签名）
3. 写出 `phone/keystore.properties`（Gradle 读密码用，已 gitignore）

密码来源优先级：`BANDPAD_PASS` 环境变量 → 已有的 `keystore.properties` → 随机生成。**随机生成的那个密码请存好**，丢了就只能换签名（等于两端都要卸载重装）。

**两端签名一旦确定就别单独重签其中一端**，否则通道会一直报 `fingerprint verify failed`。

### 3. 出包

```bash
bash scripts/build.sh
```

产物落到 `dist/`，文件名带机型后缀（`.9pro.rpk` / `.watch6.rpk`），一眼能看出装哪个。首次运行会 `npm install` 手表端依赖（走 npmmirror 镜像），之后每个机型约 4 秒。构建脚本会用 `band/manifest.<id>.json` 和 `common/platform.<id>.js` 临时覆盖源码里的同名文件，打完自动还原，所以工作区不会留脏改动。也可以分开编：

```bash
cd band  && npm run release        # → band/dist/*.rpk
cd phone && gradle assembleRelease  # → app/build/outputs/apk/release/*.apk
```

---

## 想改手感？

| 想要的效果 | 改哪里 |
|---|---|
| 更容易 / 更难触发 | `band/src/pages/index/index.ux` 的 `THRESHOLD`（默认 28dp，调大更防误触） |
| 翻页幅度 | `phone/.../DeviceProfile.java` 的 `vFrom / vTo`（起止点占屏高比例），两个机型各一份 |
| 横滑幅度 | 同文件 `hFrom`（起点占屏宽比例，终点取 1 - hFrom；离边缘太近会被系统当成返回） |
| 连点节奏 | 同文件 `tap()` 里的 `i * 110L` 与 `40`（间隔 / 每下按压时长） |
| 连击判定窗口 | `index.ux` 的 `TAP_GAP_MS`（默认 280ms，两下间隔超过它就当两次单击） |
| 翻页快慢 | 同上，`StrokeDescription(path, 0, 170)` 的 `170` 是毫秒 |
| 防连翻间隔 | `index.ux` 的 `DEDUPE_MS`（默认 350ms） |
| 长按多久算加速 | `index.ux` 的 `LONG_PRESS_MS`（默认 420ms，调大更不容易误触） |
| 长按落在屏幕哪里 | `DeviceProfile.java` 的 `holdX / holdY`（默认 0.15 / 0.45，即左侧 15% 处；抖音只认两侧边缘区） |
| 收藏点偏了 | 同文件 `favX / favY`（兜底坐标，默认 0.93 / 0.60；优先走「找收藏按钮」） |
| 长按最长持续 | `FlipService.java` 的 `MAX_HOLD_MS`（默认 20 秒，防止「松手」指令丢失后手指按死） |
| 锁定最长持续 | 同文件 `LOCK_MAX_HOLD_MS`（默认 10 分钟，锁定后没人取消时的兜底） |
| 滑动多少算滑 | `index.ux` 的 `H_FIRE / V_FIRE`（默认 14 / 26dp，横向更小是为了抢在系统返回手势前面） |
| 下滑锁定 / 上滑取消的判定距离 | `index.ux` 的 `LOCK_THRESHOLD`（默认 24dp） |
| App 图标配色 | `scripts/gen_icon.java` 的 `BG` / `FG`，然后 `java scripts/gen_icon.java phone/app/src/main/res` |
| 手环应用图标 | `java scripts/gen_band_logo.java band/src/common/logo_bf.png 128 --like scripts/ref/logo_official_128.png`（`--like` 让输出与官方 PNG 字节同构，见下方说明） |

### 手环图标为什么这么挑剔

Vela 的图标签码器对 PNG 有**四条**硬约束，违反任何一条都会在应用列表里显示成花屏/乱码：

| 约束 | 官方 demo | 踩过的坑 |
|---|---|---|
| 尺寸 | 128×128 | 256×256 → 花屏 |
| 像素格式 | 8 位 RGB（colortype 2，无 alpha） | ARGB 32 位 → 花屏 |
| 压缩后体积 | IDAT 3408 字节（压缩比约 14:1） | 纯色大图压到 742 字节（66:1）→ 解码器按「压缩长度 × 系数」预分配缓冲不够 → 花屏 |
| **图标文件名** | `logo.png` | **只换图片内容、文件名不变 → 一直读旧缓存。改名成 `logo_bf.png` 后立即正常** |

`--like` 模式会照抄模板的 IHDR 和辅助 chunk（tEXt）、沿用模板的行过滤器策略（首行 Sub、其余 Up），并且在压缩后 IDAT 小于模板长度时自动退到不压缩，保证体积不低于 3408。所以生成出来的文件通常 49 KB 左右，这是正常的，不是写错了。

REDMI Watch 6 的应用图标用 192×192（`band/src/common/logo_w6.png`，`java scripts/gen_band_logo.java band/src/common/logo_w6.png 192` 生成），手表固件没有手环那套 3408 字节的怪癖，普通编码即可。它同样是**独立文件名**，避免命中手环那张图的缓存。

**改图标的完整动作**（少一步都会显示成旧图或花屏）：

1. 换一个新的文件名，例如 `logo_v2.png`，别覆盖原来的（缓存按「包名 + 资源路径」，同名永远读到旧位图）
2. 同步改 `src/manifest.json` 的 `"icon": "/common/logo_v2.png"`
3. bump `versionCode`，卸载旧版再装新版（覆盖安装不刷新图标位图）

---

## 原理与已踩过的坑

留在这里，省得后来者重走一遍。

**为什么不能直连蓝牙 HID**：9 Pro 的快应用沙箱里没有 BLE GATT、没有 WebSocket，手环也没有 Wi-Fi。所以手环不可能直接给自己发键鼠事件，「手环当蓝牙鼠标」这条路在 Vela 上走不通。唯一可行的是「手环发指令 → 手机用自己的无障碍权限替你滑」。

**`permission denied` ≠ 签名问题**：SDK 状态码分得很细，`-6` 才是 `fingerprint verify failed`（签名不符），`-7` 是 `permission denied`。拿到 -7 却去查签名会白折腾一天——真正原因是漏了 `AuthApi.requestPermission`。

**右滑为什么既会退出、又发不出指令**：手环上「右滑 = 系统返回」是页面级手势，不是应用实现的。删掉自己的 `router.back()` 不够，还要实现 `onBackPress()` 返回 `true` 把系统返回吃掉（只有点右上角按钮时才置 `allowExit` 放行）——这是 1.1.7 做的。

但 1.1.7 之后右滑仍然发不出横滑指令，原因是**系统手势会在滑动中途接管触摸**：页面拿到几个 `touchmove` 之后就收不到 `touchend` 了，而原来的判定写在 `touchend` 里，等于永远等不到。1.1.8 把判定挪到 `touchmove`，横滑位移超过 14dp 就立刻发指令，抢在系统前面；万一连 14dp 都拿不到，`onBackPress()` 里还留了一层兜底——只要这次触摸里检测到过「向右拖动」，就把被系统吃掉的那次右滑补发成一次横滑。

**「锁定 2 倍速」是在手机上不动手实现的**：手环下滑时只发 `lock:1`，手机把 `MAX_HOLD_MS` 的自动松开换成 10 分钟的 `LOCK_MAX_HOLD_MS`，按住的手指继续按着；`release` 到达时看到已锁定就直接忽略。手环上滑发 `lock:0`，手机才 `continueStroke` 收尾抬起手指。

**左右滑发的是「横向手势」，不是翻页**：手环只负责报方向，手机上按 `a=h` 走横向分支——在屏幕中部（`y=0.5h`）从 `0.75w` 划到 `0.25w`，起止点都离左右边缘远，不会被系统手势导航当成「返回」。

**收藏靠「找按钮」，不靠连点**：抖音没有「三击收藏」这个手势，连点三下只会变成双击点赞 + 单击暂停。所以三击走的是无障碍节点查找——遍历当前界面里 `contentDescription` 含「收藏」的节点并点击它；找不到才退回坐标点按（`0.93w / 0.60h`）。为了避免乱点，查找失败时不会退到「点评论/分享」。

**双击是同一个 `dispatchGesture` 里的两笔短按**：`tap(n)` 给每次点击建一笔 40ms 的 stroke，间隔 110ms，一次派发完成；拆成三次独立派发反而容易在低端机上被吞掉一两笔。

**长按必须按在屏幕两侧**：抖音/快手的「长按 2 倍速」只在左右两侧约 1/5 宽的区域生效，按正中间弹的是倍速调节浮层——所以落点从正中改成左侧 15%（`0.15w / 0.45h`），既在边缘区内，又避开最左边的系统返回手势带和右侧那排按钮。

**「按住加速」不是发一次长按，而是真的把手指按在屏幕上**：一次 `dispatchGesture` 的长按只会压下再抬起，抖音的 2 倍速撑不到一秒。现在的做法是手环按下时发 `press`，手机用 `willContinue=true` 的笔画落下手指并**保持不放**；手环松手时发 `release`，再用 `continueStroke(...)` 续写一笔收尾把手指抬起。中间任何一环丢了，`MAX_HOLD_MS` 到点自动松开，不会把手机卡在「一直按着」的状态。

**同一台手环只能注册一次监听**：已连通时再调 `addListener` 会被拒，而旧监听其实还在。现在的实现会识别这种「重复注册」并沿用现有通道，不会把状态刷成断开。

**手环图标的四个坑，逐个踩过**：① 尺寸必须 128×128（256 会花屏）；② 必须 RGB 24 位无 alpha（ARGB 会花屏）；③ 不能压太狠，IDAT 要 ≥ 官方那张的 3408 字节，否则解码器的预分配缓冲不够（纯色图压到 742 字节就是花屏）；④ **图标位图按「包名 + 资源路径」缓存，换图必须换文件名**——前三条都改对了、只换内容不改名，界面上仍显示之前那张坏图，改名成 `logo_bf.png` 后立刻正常。另加一条通用规律：覆盖安装不刷新图标，换图标要卸载再装。

**手环界面上有版本号**（底部 `翻 0 · 横 0 · ×2 0 · v1.1.8`），装完能直接确认是不是新包，不用靠猜。

---

## 保活（清后台还能用）

通道跑在**无障碍服务**上，而无障碍服务由系统绑定：多任务里划掉 App，进程会死，但系统发现「无障碍服务还启用着却没在跑」会**自动重新绑定**，`onServiceConnected` 再触发一次，通道自己接回来。

再叠两层：`JobScheduler` 每 15 分钟检查通道（断了就重连），加上电池白名单与自启动。

**验证**：滑手环确认正常 → 划掉 App → 等 5 秒 → 再滑，还能翻页就说明自愈生效。

**做不到的**：手动关掉无障碍、或系统在极端内存压力下清理时通道会断，打开一次 App 即恢复。微信那种「划不掉」靠的是厂商系统白名单，普通 App 申请不到，任何第三方都复制不了——所以没做「双进程互拉、1px 通知、静默播音频、循环闹钟」那些 Android 8 之后基本失效、还耗电易被冻结的偏方。

---

## 排错

| 现象 | 原因 / 处理 |
|---|---|
| 日志 `注册监听失败: permission denied` | **没申请手环权限**。点 ⑤ 申请，手环上允许，再点 ④ 重连。最常见的一条 |
| 日志「注册监听失败」但手环其实还能滑 | **重复注册**。SDK 对同一手环只允许注册一次，已连通时再点 ⑤ 会被拒。本版本会自动识别并沿用现有通道，⑤ 也会变灰 |
| 状态卡在「正在注册监听」 | 已修，该状态只在注册进行中出现。若仍卡住，点 ④ 重连（会先解绑旧通道） |
| 日志 `fingerprint verify failed` | 两端签名不同源，必须同一 keystore 重签 RPK 和 APK，并两端都卸载重装 |
| 日志「手环上没装 RPK」 | RPK 没装进去，或装的包名不是 `com.bandpad.pageflip` |
| App 一直「等待手环」 | 小米运动健康是否已连上手环；两端包名是否都为 `com.bandpad.pageflip` |
| 手环显示已连接但没反应 | 手机无障碍被系统关了，回 App 重开；HyperOS 上给它加锁 |
| 息屏一会儿就失效 | 省电策略没设「无限制」，或被清了后台 |
| 某个 App 里翻不动 | 该 App 自绘手势或屏蔽无障碍注入，系统限制，无解 |
| 右滑还是没反应 | 1.1.8 起横滑在 `touchmove` 里提前触发；若仍无反应，把运行记录发我，看是收到了「向右滑 已执行」还是压根没收到指令（后者说明系统连 `touchmove` 都没给，需要换判定方式） |
| 手环按住没加速 | 先点 ⑧ 验证手机端能否长按；能长按但抖音不加速，看是不是落点不在两侧边缘区（改 `HOLD_X_RATIO`，0.12~0.18 都可以） |
| 松手后画面还在加速 | 要么已锁定（界面显示 `2× 已锁定`，上滑取消），要么「抬起」指令丢包。未锁定时等 20 秒会自动松开；仍不行就在手机上随便点一下 |
| 手环上退不出去 | 点右上角「退出」按钮（1.1.5 起左右滑只发横滑手势，不再退出）；旧包请重装 RPK |
| Watch 6 上滑没反应 | 手表的上下滑大概率被系统手势接管。改用底部 `▲ / ▼` 按钮；想恢复滑动可以先把系统「上滑回表盘」关掉试试 |
| Watch 6 界面字特别大 / 特别小 | 装错机型包了：432×514 该用 `watch6` 那个 rpk（designWidth 216） |
| 三击没收藏 | 看运行记录里是「已点中收藏按钮」还是「没找到按钮节点」。后者说明当前界面没暴露收藏按钮，把 `FAV_X_RATIO / FAV_Y_RATIO` 调成你屏幕上星标按钮的位置 |
| 双击没点赞 | 双击落在画面正中；若中间有弹窗（评论、分享）遮着，先返回再试 |
| 横滑被识别成「返回」 | 横滑起止点在屏幕 0.75w / 0.25w，若你的系统把「屏幕边缘滑动」阈值设得很宽，改 `FlipService.java` 里的 0.75 / 0.25 往里收 |
| AstroBox 装不上 RPK | 确认 RPK 没被浏览器改后缀；同版本号不会更替，需 bump `versionCode` |
| 提示签名不正确 | 两端证书不一致，重跑 `bash scripts/gen-pem.sh` 后两端都重出包并重装 |

---

## 权限与隐私

- 无障碍服务声明了 `canPerformGestures`（模拟手势）和 `canRetrieveWindowContent`（读界面结构）。后者**只在三击收藏时用一次**：在当前界面里找 `contentDescription` 含「收藏」的按钮并点击，找不到就按坐标点一下；不截屏、不保存、不上传任何界面内容。翻页和长按完全不用它。
- 没有常驻通知，没有联网权限，没有分析统计；翻页指令只在本机 BLE 通道里传。
- 手环端 `manifest.json` 只申请 `system.interconnect` 与 `system.vibrator`。

## 免责

自用工具，未上架任何商店，按现状提供、不保证在所有 Vela 设备上可用。给设备侧载第三方快应用存在风险，刷机/装包前请自行确认能承担后果；手环固件大版本更新后，侧载通道有可能收紧。

## 致谢

- 小米穿戴 SDK（`xms-wearable-lib 1.4`）与官方 interconnect 示例包
- [AstroBox](https://astrobox.online/) —— 手环端快应用的刷入工具
- 米坛社区（BandBBS）的先行探索

## 许可证

本仓库自有代码以 [MIT](LICENSE) 发布。

例外：`phone/app/libs/xms-wearable-lib_1.4_release.aar` 是小米的闭源穿戴 SDK，版权归小米，
随仓库提供只为让你能直接编译，不适用 MIT。详见 [NOTICE.md](NOTICE.md)。

## 安全说明

- 仓库**不含**任何私钥、keystore 或明文密码；`phone/keystore/`、`band/sign/`、`*.pem`、`*.p12`、`keystore.properties`、`local.properties` 全部在 `.gitignore` 里。
- 如果你 fork 后自己跑过 `gen-pem.sh`，请确认 `git status` 里没有出现 `.pem` 或 `.p12` 再 push。
- 无障碍权限等于「允许代替你操作屏幕」，请只安装你信任的版本；本项目的源码全部公开，可自行编译核对。
