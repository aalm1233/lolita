package com.lolita.app.domain.model

/** Local, pre-enqueue failures only. No server text, image path, credential or original cause. */
enum class MannequinAdmissionFailureKind(val message:String) {
    SUBJECT("物品分类或展示方式已变化，请重新核对分类后确认；本次未发送请求"),
    REFERENCE("物品或参考图片已变化，请重新选择当前图片后确认；本次未发送请求"),
    TEMPLATE("展示模板读取或校验失败，请重新进入页面或明确选择头模；本次未发送请求"),
    SOURCE("原图无法完整读取或不符合图片要求，请重新导入或选择清楚的图片；本次未发送请求"),
    INPUT_CHANGED("输入图片在准备时发生变化，请重新选择并核对；本次未发送请求"),
    STORAGE("本机任务保存失败，请检查可用空间后重试；本次未发送请求")
}

class MannequinAdmissionFailure(val kind:MannequinAdmissionFailureKind):IllegalStateException(kind.message)
