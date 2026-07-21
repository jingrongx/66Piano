# 钢琴学院 PianoKids

面向 4–12 岁儿童的原生安卓钢琴学习 APP。零基础也能从认识琴键一路学到完整曲目：实时听音准、识别曲子对错、动画教学、闯关考验、宠物养成。

## 功能（P1 MVP）

- 🎤 **实时调音器** — YIN 算法 + NDK C++ FFT，仪表盘式可视化音准偏差
- 📚 **第 1 课教学** — "认识钢琴"完整 5 步流程：看动画 → 看示范 → 跟我弹 → 自己弹 → 领奖励
- 🎵 **练琴模式** — 简化五线谱 + 实时弹对/弹错反馈（DTW 算法）
- 🐣 **豆豆宠物** — 随练习成长，经验等级系统
- ⭐ **星星奖励** — 完成课程/关卡获得星星
- 🔒 **家长锁** — 设置入口（P2）
- 📊 **家长报告** — 练习时长/准确率趋势（P2）

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 | Kotlin 2.0 + C++17 (NDK) |
| UI | Jetpack Compose + Material 3 |
| 音频采集 | AudioRecord (44.1kHz / mono / 16bit) |
| 音频算法 | 自实现 KissFFT (radix-2) + YIN 基频检测 + onset 检测 |
| 曲谱比对 | DTW 动态时间规整 |
| MIDI | 自研轻量 SMF 解析器 |
| 存储 | Room (SQLite) + DataStore Preferences |
| DI | Hilt |
| 架构 | MVVM + Clean Architecture |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |

## 项目结构

```
PianoKids/
├── app/src/main/
│   ├── cpp/                        # NDK C++ 音频算法
│   │   ├── CMakeLists.txt
│   │   ├── kiss_fft.{h,cpp}        # 轻量 FFT
│   │   ├── pitch_yin.{h,cpp}       # YIN 基频检测
│   │   ├── note_detector.{h,cpp}   # 音名转换 + onset
│   │   └── jni_bridge.cpp          # JNI 桥接
│   ├── java/com/pianokids/
│   │   ├── audio/                  # 音频采集/检测/识别/前台服务
│   │   ├── music/                  # MIDI 解析 + DTW 比对
│   │   ├── data/                   # Room 数据库 + DataStore + Repository
│   │   ├── di/                     # Hilt 模块
│   │   ├── ui/                     # Compose UI (5 个 Tab + 公共组件)
│   │   │   ├── home/ learn/ tuner/ practice/ pet/
│   │   │   ├── components/         # 钢琴键盘、星星计数器
│   │   │   └── theme/              # Material 3 主题
│   │   ├── MainActivity.kt
│   │   └── PianoKidsApp.kt
│   └── res/                        # 颜色、字符串、主题、通知图标
├── gradle/libs.versions.toml       # 版本目录
├── build.gradle.kts
└── settings.gradle.kts
```

## 构建

需要 JDK 17 + Android SDK (compileSdk 34) + NDK + CMake。

```bash
# 使用 Gradle 8.x
gradle :app:assembleDebug

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

### 环境示例（mise 管理多版本 Java）

```bash
mise exec java@17.0.2 -- gradle :app:assembleDebug
```

## 权限

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 听孩子弹琴，做音准/曲谱识别 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | 练琴时持续监听不被打断 |
| `VIBRATE` | 弹对/弹错的触觉反馈 |
| `WAKE_LOCK` | 练琴时屏幕不息屏 |

## 后续路线

- **P2**：完整 6 级学习路径、闯关大冒险（听音/节奏/视奏）、装扮商店、家长报告
- **P3**：曲库扩充（导入外部 MIDI）、云同步进度、TTS 语音引导
