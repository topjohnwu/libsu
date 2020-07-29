/*
 * Copyright 2020 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

extern "C" JNIEXPORT JNICALL
jint Java_com_topjohnwu_libsuexample_AIDLService_nativeGetUid(
		JNIEnv *env, jobject instance) {
	return getuid();
}

extern "C" JNIEXPORT JNICALL
jstring Java_com_topjohnwu_libsuexample_AIDLService_nativeReadFile(
		JNIEnv *env, jobject instance, jstring name) {
	const char *path = env->GetStringUTFChars(name, nullptr);
	int fd = open(path, O_RDONLY);
	env->ReleaseStringUTFChars(name, path);
	char buf[4096];
	buf[read(fd, buf, sizeof(buf) - 1)] = 0;
	return env->NewStringUTF(buf);
}
