#include <jni.h>
#include <vips/vips.h>
#include <limits.h>
#include <math.h>

/* JNI owns input and encoded buffers; every exit releases the entire lazy image graph first. */
static void throw_error(JNIEnv *env, const char *type, const char *message) {
    if (!(*env)->ExceptionCheck(env)) {
        jclass klass = (*env)->FindClass(env, type);
        if (klass) (*env)->ThrowNew(env, klass, message);
    }
}

JNIEXPORT void JNICALL
Java_com_sssok_renewal_outgoing_processing_image_LibvipsImageProcessor_00024Native_init(
    JNIEnv *env, jclass klass) {
    (void) klass;
    if (VIPS_INIT("sssok-image")) {
        throw_error(env, "java/lang/IllegalStateException", vips_error_buffer());
        vips_error_clear();
        return;
    }
    /* The Java image worker pool bounds parallel jobs. Do not multiply it by CPU count. */
    vips_concurrency_set(1);
    vips_cache_set_max(0);
}

static jbyteArray encode(JNIEnv *env, VipsImage *shared, int width, int height, float quality) {
    VipsImage *scaled = NULL;
    void *buffer = NULL;
    size_t length = 0;
    jbyteArray output = NULL;
    if (vips_resize(shared, &scaled, (double) width / shared->Xsize,
        "vscale", (double) height / shared->Ysize, "kernel", VIPS_KERNEL_LINEAR, NULL)) goto done;
    if (vips_webpsave_buffer(scaled, &buffer, &length,
        "Q", (int) roundf(quality), "effort", 0, "strip", TRUE, NULL)) goto done;
    if (length > INT_MAX) {
        throw_error(env, "java/lang/IllegalStateException", "Encoded image exceeds Java array limit");
        goto done;
    }
    output = (*env)->NewByteArray(env, (jsize) length);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize) length, buffer);
done:
    g_free(buffer);
    VIPS_UNREF(scaled);
    return output;
}

JNIEXPORT jobject JNICALL
Java_com_sssok_renewal_outgoing_processing_image_LibvipsImageProcessor_00024Native_derive(
    JNIEnv *env, jclass klass, jbyteArray source, jint thumb_width, jfloat thumb_quality,
    jint preview_width, jfloat preview_quality) {
    (void) klass;
    void *input = NULL;
    VipsImage *header = NULL, *base = NULL, *shared = NULL;
    jobject result = NULL;
    jbyteArray thumbnail = NULL, preview = NULL;
    jsize length = (*env)->GetArrayLength(env, source);
    if (length <= 0 || length > 150 * 1024 * 1024) {
        throw_error(env, "java/lang/IllegalArgumentException", "JPEG input size exceeds limit");
        goto cleanup;
    }
    input = g_try_malloc((size_t) length);
    if (!input) {
        throw_error(env, "java/lang/OutOfMemoryError", "Cannot allocate JPEG input");
        goto cleanup;
    }
    (*env)->GetByteArrayRegion(env, source, 0, length, input);
    if ((*env)->ExceptionCheck(env)) goto cleanup;
    /* Header only: no full-size decode. Restrict this entry point to the measured JPEG loader. */
    if (vips_jpegload_buffer(input, length, &header, "access", VIPS_ACCESS_SEQUENTIAL, NULL)) goto error;
    int width = header->Xsize, height = header->Ysize, orientation = 1;
    if (vips_image_get_typeof(header, VIPS_META_ORIENTATION))
        vips_image_get_int(header, VIPS_META_ORIENTATION, &orientation);
    if (orientation >= 5 && orientation <= 8) {
        int swap = width; width = height; height = swap;
    }
    if (width <= 0 || height <= 0) goto error;
    int target_width = MIN(MAX(thumb_width, preview_width), width);
    int target_height = (int) round((double) height * target_width / width);
    target_height = MAX(1, target_height);
    if (width <= 0 || height <= 0 || (gint64) width * height > 100000000
        || target_width > 16383 || target_height > 16383
        || (gint64) target_width * target_height > 16000000) {
        throw_error(env, "java/lang/IllegalArgumentException", "JPEG dimensions exceed processing limit");
        goto cleanup;
    }
    VIPS_UNREF(header);
    /* Upright and shrink-on-load. Materialize once to share pre-encode pixels between variants. */
    if (vips_thumbnail_buffer(input, length, &base, target_width,
        "height", target_height, "size", VIPS_SIZE_FORCE,
        "option_string", "fail_on=error", NULL)) goto error;
    shared = vips_image_copy_memory(base);
    if (!shared) goto error;
    int tw = MIN(thumb_width, width);
    int th = MAX(1, (int) round((double) height * tw / width));
    thumbnail = encode(env, shared, tw, th, thumb_quality);
    if (!thumbnail || (*env)->ExceptionCheck(env)) goto error;
    if (preview_width > 0) {
        int pw = MIN(preview_width, width);
        int ph = MAX(1, (int) round((double) height * pw / width));
        preview = encode(env, shared, pw, ph, preview_quality);
        if (!preview || (*env)->ExceptionCheck(env)) goto error;
    }
    jclass result_type = (*env)->FindClass(env,
        "com/sssok/renewal/outgoing/processing/image/LibvipsImageProcessor$NativeResult");
    if (!result_type) goto cleanup;
    jmethodID constructor = (*env)->GetMethodID(env, result_type, "<init>", "(II[B[B)V");
    if (!constructor) goto cleanup;
    result = (*env)->NewObject(env, result_type, constructor, width, height, thumbnail, preview);
    goto cleanup;
error:
    /* Do not classify a native encoder/allocator error as a permanently broken original. */
    throw_error(env, "java/lang/IllegalStateException", vips_error_buffer());
cleanup:
    vips_error_clear();
    VIPS_UNREF(shared);
    VIPS_UNREF(base);
    VIPS_UNREF(header);
    g_free(input);
    return result;
}
