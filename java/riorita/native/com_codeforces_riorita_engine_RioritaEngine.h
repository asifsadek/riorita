/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_codeforces_riorita_engine_RioritaEngine */

#ifndef _Included_com_codeforces_riorita_engine_RioritaEngine
#define _Included_com_codeforces_riorita_engine_RioritaEngine
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    initialize
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_initialize
  (JNIEnv *, jobject, jstring, jint);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    has
 * Signature: (Ljava/lang/String;Ljava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_has
  (JNIEnv *, jobject, jstring, jstring, jlong);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    get
 * Signature: (Ljava/lang/String;Ljava/lang/String;J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_get
  (JNIEnv *, jobject, jstring, jstring, jlong);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    put
 * Signature: (Ljava/lang/String;Ljava/lang/String;[BJJZ)Z
 */
JNIEXPORT jboolean JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_put
  (JNIEnv *, jobject, jstring, jstring, jbyteArray, jlong, jlong, jboolean);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    erase
 * Signature: (Ljava/lang/String;Ljava/lang/String;J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_erase__Ljava_lang_String_2Ljava_lang_String_2J
  (JNIEnv *, jobject, jstring, jstring, jlong);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    erase
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_erase__Ljava_lang_String_2
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    clear
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_clear
  (JNIEnv *, jobject);

/*
 * Class:     com_codeforces_riorita_engine_RioritaEngine
 * Method:    id
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_codeforces_riorita_engine_RioritaEngine_id
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif