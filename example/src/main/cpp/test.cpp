//
// Created by John Wu on 7/26/20.
//
#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <string>

extern "C" JNIEXPORT JNICALL
jint Java_com_topjohnwu_libsuexample_MainActivity_00024ExampleService_nativeGetUid(
		JNIEnv *env, jobject instance) {
	return getuid();
}

extern "C" JNIEXPORT JNICALL
jstring Java_com_topjohnwu_libsuexample_MainActivity_00024ExampleService_nativeReadFile(
		JNIEnv *env, jobject instance, jstring name) {
	const char *path = env->GetStringUTFChars(name, nullptr);
	int fd = open(path, O_RDONLY);
	env->ReleaseStringUTFChars(name, path);
	char buf[4096];
	buf[read(fd, buf, sizeof(buf) - 1)] = 0;
	return env->NewStringUTF(buf);
}
