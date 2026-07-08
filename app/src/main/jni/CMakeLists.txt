cmake_minimum_required(VERSION 3.10)

project(floatwindowdemo)

# 引入 NCNN
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# 支持 Android 16KB page size
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# 定义我们自己的库
add_library(yolov8n_ncnn SHARED
            yolov8n_jni.cpp)

# 链接库
target_link_libraries(yolov8n_ncnn
                    ncnn
                    android
                    log
                    jnigraphics) # 用于直接处理 Bitmap
