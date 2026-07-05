#include <jni.h>

#include <openssl/crypto.h>
#include <openssl/ssl.h>

// Initialize OpenSSL once per process and avoid atexit()-based teardown, which races
// with pthread TLS destructors when Kotlin coroutine IO threads exit.
JNIEXPORT jint JNI_OnLoad(JavaVM * /*vm*/, void * /*reserved*/)
{
    OPENSSL_init_ssl(
        OPENSSL_INIT_LOAD_SSL_STRINGS | OPENSSL_INIT_LOAD_CRYPTO_STRINGS | OPENSSL_INIT_NO_ATEXIT,
        nullptr);
    return JNI_VERSION_1_6;
}
