#include <jni.h>
#include <string>
#include <algorithm>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_browser_MainActivity_processUrl(
        JNIEnv* env,
        jobject /* this */,
        jstring url) {
    const char* nativeUrl = env->GetStringUTFChars(url, nullptr);
    std::string urlStr(nativeUrl);
    env->ReleaseStringUTFChars(url, nativeUrl);

    // Basic logic: if it doesn't contain a dot, treat as search
    // If it doesn't start with http, add https://
    
    if (urlStr.find('.') == std::string::npos) {
        // Treat as Google Search
        return env->NewStringUTF( ("https://www.google.com/search?q=" + urlStr).c_str() );
    }

    if (urlStr.find("://") == std::string::npos) {
        urlStr = "https://" + urlStr;
    }

    return env->NewStringUTF(urlStr.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_browser_MainActivity_getBrowserName(
        JNIEnv* env,
        jobject /* this */) {
    std::string name = "Velocity Browser";
    return env->NewStringUTF(name.c_str());
}
