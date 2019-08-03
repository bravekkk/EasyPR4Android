#ifndef MRCAR_H
#define MRCAR_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
init(JNIEnv *env, jclass type,jstring dir);

JNIEXPORT jstring JNICALL
plateRecognition(JNIEnv *env, jclass type, jlong matImg, jlong matResult);

JNIEXPORT jint JNICALL
release(JNIEnv *env, jclass type);

#ifdef __cplusplus
}
#endif

#endif