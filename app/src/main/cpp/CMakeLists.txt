cmake_minimum_required(VERSION 3.10.2)
project("webrtc_vad_jni")

add_library(webrtc_vad_jni SHARED webrtc_vad_jni.cpp simple_vad.c)

target_include_directories(webrtc_vad_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)

target_link_libraries(webrtc_vad_jni ${log-lib})
