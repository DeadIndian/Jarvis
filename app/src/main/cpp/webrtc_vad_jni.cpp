#include <jni.h>
#include <cstring>
#include "simple_vad.h"

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_app_vad_WebRtcVadNative_init(JNIEnv* env, jclass clazz, jint aggressiveness) {
    (void)env; (void)clazz;
    vad_init((int)aggressiveness);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jarvis_app_vad_WebRtcVadNative_isSpeech(JNIEnv* env, jclass clazz, jshortArray pcmArray, jint length) {
    (void)clazz;
    if (pcmArray == nullptr || length <= 0) return JNI_FALSE;
    jshort* arr = env->GetShortArrayElements(pcmArray, NULL);
    if (arr == nullptr) return JNI_FALSE;
    int detected = vad_process((const short*)arr, (int)length);
    env->ReleaseShortArrayElements(pcmArray, arr, JNI_ABORT);
    return detected ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_app_vad_WebRtcVadNative_release(JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    vad_release();
}
