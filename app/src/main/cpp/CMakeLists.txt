# For more information about using CMake with Android NDK, see the documentation:
# https://d.android.com/studio/projects/add-native-code.html

# 设置 CMake 最小版本
cmake_minimum_required(VERSION 3.22.1)

# 声明并命名项目
project("pianokids")

# 创建 native 库 piano_audio
add_library(
        piano_audio             # 库名称
        SHARED                  # 动态库
        kiss_fft.cpp            # FFT 实现
        pitch_yin.cpp           # YIN 基频检测
        note_detector.cpp       # 音符 onset 检测与音名转换
        jni_bridge.cpp          # JNI 桥接
)

# 包含当前 cpp 目录，便于引用头文件
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 设置 C++17 标准，开启 -O3 -ffast-math 优化（与 build.gradle.kts 中的 cppFlags 保持一致）
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 编译选项：开启优化与快速数学运算
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")
endif()

# 查找 Android log 库（用于 __android_log_print）
find_library(
        log-lib
        log
)

# 链接 log 库到 piano_audio
target_link_libraries(
        piano_audio
        ${log-lib}
)
