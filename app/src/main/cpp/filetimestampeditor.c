#include <jni.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <time.h>
#include <errno.h>
#include <string.h>

void updateErrorMessage(JNIEnv *env, jobject thiz, const char *message) {
    jclass thisClass = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, thisClass, "errMsg", "Ljava/lang/String;");
    if (fid == NULL) {
        fprintf(stderr, "Java field errMsg has not been found.\n");
        return; // Field not found
    }
    jstring jstr = (*env)->NewStringUTF(env, message);
    (*env)->SetObjectField(env, thiz, fid, jstr);
}

JNIEXPORT jboolean JNICALL
Java_com_luis_filetimestampeditor_MainActivity_modifyTimestamp(JNIEnv *env, jobject thiz, jint fd,
                                                               jint year, jint month, jint day,
                                                               jint hour, jint minute) {
    struct timespec times[2];
    struct tm newtime;
    newtime.tm_year = year - 1900;
    newtime.tm_mon = month - 1;
    newtime.tm_mday = day;
    newtime.tm_hour = hour;
    newtime.tm_min = minute;
    newtime.tm_sec = 0;
    newtime.tm_isdst = -1;

    time_t specific_time = mktime(&newtime);
    if (specific_time == -1) {
        updateErrorMessage(env, thiz, "Error converting time with mktime");
        close(fd);
        return JNI_FALSE;
    }

    times[0].tv_sec = specific_time;
    times[0].tv_nsec = 0;
    times[1].tv_sec = specific_time;
    times[1].tv_nsec = 0;

    if (futimens(fd, times) == -1) {
        updateErrorMessage(env, thiz, strerror(errno));
        close(fd);
        return JNI_FALSE;
    }
    close(fd);
    return JNI_TRUE;
}
