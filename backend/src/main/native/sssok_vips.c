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

/* Keep stage slots and fields aligned with NativeImageDiagnostics.java. */
typedef enum {
    STAGE_HEADER = 0, STAGE_DECODE = 1, STAGE_RESIZE_SHARED = 2,
    STAGE_RESIZE_THUMBNAIL = 3, STAGE_ENCODE_THUMBNAIL = 4,
    STAGE_RESIZE_PREVIEW = 5, STAGE_ENCODE_PREVIEW = 6,
    STAGE_COUNT = 7
} DiagnosticStage;

typedef enum {
    TIMING_START_MICROS, TIMING_DURATION_MICROS, TIMING_SUCCESS, TIMING_FIELD_COUNT
} TimingField;

#define TIMING_VALUE_COUNT (STAGE_COUNT * TIMING_FIELD_COUNT)

typedef struct {
    jlong values[TIMING_VALUE_COUNT];
    gint64 epoch, origin, started;
    int active;
    gboolean enabled;
} StageTimes;

static void stage_start(StageTimes *t, DiagnosticStage stage) {
    if (!t->enabled) return;
    t->active = stage;
    t->started = g_get_monotonic_time();
    t->values[stage * TIMING_FIELD_COUNT + TIMING_START_MICROS] = t->epoch + t->started - t->origin;
}

static void stage_end(StageTimes *t, gboolean success) {
    if (!t->enabled || t->active < 0) return;
    t->values[t->active * TIMING_FIELD_COUNT + TIMING_DURATION_MICROS] = g_get_monotonic_time() - t->started;
    t->values[t->active * TIMING_FIELD_COUNT + TIMING_SUCCESS] = success;
    t->active = -1;
}

/* Export failed-stage timings without replacing the original JNI exception. */
static void export_timings(JNIEnv *env, jlongArray output, const StageTimes *times) {
    if (!output) return;
    jthrowable failure = (*env)->ExceptionOccurred(env);
    if (failure) (*env)->ExceptionClear(env);
    (*env)->SetLongArrayRegion(env, output, 0, TIMING_VALUE_COUNT, times->values);
    if (failure) {
        (*env)->Throw(env, failure);
        (*env)->DeleteLocalRef(env, failure);
    }
}

static jbyteArray encode(JNIEnv *env, VipsImage *shared, int width, int height, float quality,
    StageTimes *times, DiagnosticStage resize_stage, DiagnosticStage encode_stage) {
    VipsImage *scaled = NULL, *pixels = NULL;
    void *buffer = NULL;
    size_t length = 0;
    jbyteArray output = NULL;
    stage_start(times, resize_stage);
    if (vips_resize(shared, &scaled, (double) width / shared->Xsize,
        "vscale", (double) height / shared->Ysize, "kernel", VIPS_KERNEL_LINEAR, NULL)) goto done;
    if (times->enabled) {
        pixels = vips_image_copy_memory(scaled);
        if (!pixels) goto done;
    }
    stage_end(times, TRUE);
    stage_start(times, encode_stage);
    if (vips_webpsave_buffer(pixels ? pixels : scaled, &buffer, &length,
        "Q", (int) roundf(quality), "effort", 0, "strip", TRUE, NULL)) goto done;
    stage_end(times, TRUE);
    if (length > INT_MAX) {
        throw_error(env, "java/lang/IllegalStateException", "Encoded image exceeds Java array limit");
        goto done;
    }
    output = (*env)->NewByteArray(env, (jsize) length);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, (jsize) length, buffer);
done:
    stage_end(times, FALSE);
    VIPS_UNREF(pixels);
    g_free(buffer);
    VIPS_UNREF(scaled);
    return output;
}

/* libvips 8.18 leaves at least 2x resolution for its final resize. */
static int jpeg_shrink_factor(double shrink) {
    if (shrink >= 16) return 8;
    if (shrink >= 8) return 4;
    if (shrink >= 4) return 2;
    return 1;
}

static VipsImage *prepare_shared_pixels(void *input, jsize length, int width, int height,
    int target_width, int target_height, StageTimes *times) {
    VipsImage *base = NULL, *loaded = NULL, *decoded = NULL, *shared = NULL;
    if (times->enabled) {
        double shrink = MIN((double) width / target_width, (double) height / target_height);
        int factor = jpeg_shrink_factor(shrink);
        stage_start(times, STAGE_DECODE);
        if (vips_jpegload_buffer(input, length, &loaded, "shrink", factor,
            "access", VIPS_ACCESS_SEQUENTIAL, "fail_on", VIPS_FAIL_ON_ERROR, NULL)) goto done;
        decoded = vips_image_copy_memory(loaded);
        if (!decoded) goto done;
        stage_end(times, TRUE);
        stage_start(times, STAGE_RESIZE_SHARED);
        if (vips_thumbnail_image(decoded, &base, target_width,
            "height", target_height, "size", VIPS_SIZE_FORCE, NULL)) goto done;
    } else {
        /* Original fused path stays unchanged for production performance measurements. */
        if (vips_thumbnail_buffer(input, length, &base, target_width,
            "height", target_height, "size", VIPS_SIZE_FORCE,
            "option_string", "fail_on=error", NULL)) goto done;
    }
    shared = vips_image_copy_memory(base);
    if (!shared) goto done;
    stage_end(times, TRUE);
done:
    VIPS_UNREF(base);
    VIPS_UNREF(decoded);
    VIPS_UNREF(loaded);
    return shared;
}

JNIEXPORT jobject JNICALL
Java_com_sssok_renewal_outgoing_processing_image_LibvipsImageProcessor_00024Native_derive(
    JNIEnv *env, jclass klass, jbyteArray source, jint thumb_width, jfloat thumb_quality,
    jint preview_width, jfloat preview_quality, jlongArray timing_output) {
    (void) klass;
    void *input = NULL;
    VipsImage *header = NULL, *shared = NULL;
    StageTimes times = {.epoch = g_get_real_time(), .origin = g_get_monotonic_time(),
        .active = -1, .enabled = timing_output != NULL};
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
    stage_start(&times, STAGE_HEADER);
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
    stage_end(&times, TRUE);
    shared = prepare_shared_pixels(input, length, width, height, target_width, target_height, &times);
    if (!shared) goto error;
    int tw = MIN(thumb_width, width);
    int th = MAX(1, (int) round((double) height * tw / width));
    thumbnail = encode(env, shared, tw, th, thumb_quality, &times, STAGE_RESIZE_THUMBNAIL, STAGE_ENCODE_THUMBNAIL);
    if (!thumbnail || (*env)->ExceptionCheck(env)) goto error;
    if (preview_width > 0) {
        int pw = MIN(preview_width, width);
        int ph = MAX(1, (int) round((double) height * pw / width));
        preview = encode(env, shared, pw, ph, preview_quality, &times, STAGE_RESIZE_PREVIEW, STAGE_ENCODE_PREVIEW);
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
    stage_end(&times, FALSE);
    export_timings(env, timing_output, &times);
    vips_error_clear();
    VIPS_UNREF(shared);
    VIPS_UNREF(header);
    g_free(input);
    return result;
}
