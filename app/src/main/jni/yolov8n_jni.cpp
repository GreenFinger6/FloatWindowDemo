#include <jni.h>
#include <android/bitmap.h>
#include <android/asset_manager_jni.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include "net.h"
#include "cpu.h"

// 在 cpp 文件顶部定义全局分配器
static ncnn::UnlockedPoolAllocator blob_pool_allocator;
static ncnn::PoolAllocator workspace_pool_allocator;

// --- 基础结构体定义 (参考示例) ---
struct Object {
    float x;
    float y;
    float w;
    float h;
    int label;
    float prob;
};

struct GridAndStride {
    int grid0;
    int grid1;
    int stride;
};

static ncnn::Net yolov8;
static int target_size = 640;

// --- 辅助函数 (参考示例) ---
static inline float sigmoid(float x) {
    return 1.0f / (1.0f + expf(-x));
}

static void generate_grids_and_stride(int target_w, int target_h, std::vector<int>& strides, std::vector<GridAndStride>& grid_strides) {
    for (int i = 0; i < (int)strides.size(); i++) {
        int stride = strides[i];
        int num_grid_w = target_w / stride;
        int num_grid_h = target_h / stride;
        for (int g1 = 0; g1 < num_grid_h; g1++) {
            for (int g0 = 0; g0 < num_grid_w; g0++) {
                GridAndStride gs;
                gs.grid0 = g0;
                gs.grid1 = g1;
                gs.stride = stride;
                grid_strides.push_back(gs);
            }
        }
    }
}

static void qsort_descent_inplace(std::vector<Object>& faceobjects, int left, int right) {
    int i = left; int j = right;
    float p = faceobjects[(left + right) / 2].prob;
    while (i <= j) {
        while (faceobjects[i].prob > p) i++;
        while (faceobjects[j].prob < p) j--;
        if (i <= j) {
            std::swap(faceobjects[i], faceobjects[j]);
            i++; j--;
        }
    }
    if (left < j) qsort_descent_inplace(faceobjects, left, j);
    if (i < right) qsort_descent_inplace(faceobjects, i, right);
}

static void nms_sorted_bboxes(const std::vector<Object>& faceobjects, std::vector<int>& picked, float nms_threshold) {
    picked.clear();
    const int n = faceobjects.size();
    std::vector<float> areas(n);
    for (int i = 0; i < n; i++) areas[i] = faceobjects[i].w * faceobjects[i].h;
    for (int i = 0; i < n; i++) {
        const Object& a = faceobjects[i];
        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++) {
            const Object& b = faceobjects[picked[j]];
            float x1 = std::max(a.x, b.x);
            float y1 = std::max(a.y, b.y);
            float x2 = std::min(a.x + a.w, b.x + b.w);
            float y2 = std::min(a.y + a.h, b.y + b.h);
            float inter_area = std::max(0.f, x2 - x1) * std::max(0.f, y2 - y1);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            if (inter_area / union_area > nms_threshold) keep = 0;
        }
        if (keep) picked.push_back(i);
    }
}

// --- JNI 接口实现 ---

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_floatwindowdemo_utils_YoloUtil_initModel(JNIEnv *env, jobject thiz, jobject assetManager) {
    // 强制设置大核
    ncnn::set_cpu_powersave(2); // 0=all, 1=little, 2=big
    ncnn::Option opt;
    opt.blob_allocator = &blob_pool_allocator;      // 优化中间层内存
    opt.workspace_allocator = &workspace_pool_allocator; // 优化工作空间
    opt.use_vulkan_compute = false; // 手机端建议先用 CPU 测试
    opt.num_threads = ncnn::get_big_cpu_count(); // 自动获取大核数量，通常是 4
    yolov8.opt = opt;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    yolov8.load_param(mgr, "yolov8n.param");
    yolov8.load_model(mgr, "yolov8n.bin");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_floatwindowdemo_utils_YoloUtil_detect(JNIEnv *env, jobject thiz, jobject bitmap,
                                                       jfloat prob_threshold, jfloat nms_threshold) {
    AndroidBitmapInfo info;
    void* pixels;
    AndroidBitmap_getInfo(env, bitmap, &info);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    int width = info.width;
    int height = info.height;

    // 1. 图像预处理 (完全参照示例的 Letterbox 逻辑)
    float scale = 1.f;
    int w = width;
    int h = height;
    if (w > h) {
        scale = (float)target_size / w;
        w = target_size;
        h = h * scale;
    } else {
        scale = (float)target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize((const unsigned char*)pixels, ncnn::Mat::PIXEL_RGBA2RGB, width, height, w, h);

    // 填充到 640x640 (Pad)
    int wpad = (w + 31) / 32 * 32 - w;
    int hpad = (h + 31) / 32 * 32 - h;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 0.f);

    const float norm_vals[3] = {1/255.f, 1/255.f, 1/255.f};
    in_pad.substract_mean_normalize(0, norm_vals);

    // 2. 推理
    ncnn::Extractor ex = yolov8.create_extractor();
    ex.input("images", in_pad);
    ncnn::Mat out;
    ex.extract("output", out);

    // 3. 生成 Proposals (核心逻辑参考示例)
    std::vector<int> strides = {8, 16, 32};
    std::vector<GridAndStride> grid_strides;
    generate_grids_and_stride(in_pad.w, in_pad.h, strides, grid_strides);

    const int num_points = grid_strides.size();
    const int num_class = out.w - 64;
    const int reg_max_1 = 16;
    std::vector<Object> proposals;

    for (int i = 0; i < num_points; i++) {
        const float* ptr = out.row(i);
        const float* scores = ptr + 64; // 分类得分从 64 开始

        // 找最大分数的类别
        int label = -1;
        float score = -1e10f;
        for (int k = 0; k < num_class; k++) {
            if (scores[k] > score) {
                label = k;
                score = scores[k];
            }
        }

        float box_prob = sigmoid(score);
        if (box_prob >= prob_threshold) {
            // DFL 解码 (手动实现示例中的 Softmax 效果)
            float pred_ltrb[4];
            for (int k = 0; k < 4; k++) {
                float dis = 0.f;
                const float* dis_ptr = ptr + (k * reg_max_1);
                // 简化的 Softmax 期望值计算
                float sum = 0.f;
                for (int l = 0; l < 16; l++) sum += expf(dis_ptr[l]);
                for (int l = 0; l < 16; l++) dis += l * (expf(dis_ptr[l]) / sum);
                pred_ltrb[k] = dis * grid_strides[i].stride;
            }

            float pb_cx = (grid_strides[i].grid0 + 0.5f) * grid_strides[i].stride;
            float pb_cy = (grid_strides[i].grid1 + 0.5f) * grid_strides[i].stride;

            float x0 = pb_cx - pred_ltrb[0];
            float y0 = pb_cy - pred_ltrb[1];
            float x1 = pb_cx + pred_ltrb[2];
            float y1 = pb_cy + pred_ltrb[3];

            Object obj;
            // 还原到原始图像比例 (减去 Padding)
            obj.x = (x0 - (wpad / 2)) / scale;
            obj.y = (y0 - (hpad / 2)) / scale;
            obj.w = (x1 - x0) / scale;
            obj.h = (y1 - y0) / scale;
            obj.label = label;
            obj.prob = box_prob;
            proposals.push_back(obj);
        }
    }

    // 4. NMS & 排序
    if (!proposals.empty()) {
        qsort_descent_inplace(proposals, 0, proposals.size() - 1);
    }
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    // 5. 封装返回 Java 结果
    jclass resClass = env->FindClass("com/example/floatwindowdemo/utils/DetectionResult");
    jmethodID resConstructor = env->GetMethodID(resClass, "<init>", "(FFFFIF)V");
    jobjectArray ret = env->NewObjectArray(picked.size(), resClass, nullptr);

    for (int i = 0; i < (int)picked.size(); i++) {
        const Object& obj = proposals[picked[i]];
        // 做一下边界裁剪，防止越界
        float rx = std::max(0.f, std::min(obj.x, (float)width));
        float ry = std::max(0.f, std::min(obj.y, (float)height));
        jobject resObj = env->NewObject(resClass, resConstructor, rx, ry, obj.w, obj.h, obj.label, obj.prob);
        env->SetObjectArrayElement(ret, i, resObj);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return ret;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_floatwindowdemo_utils_YoloUtil_release(JNIEnv *env, jobject thiz) {
    yolov8.clear(); // 清空网络层数据
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();
}