package com.napp.auto

object Config {
    const val TAG = "NappAuto"

    // 模板匹配置信度
    const val CONFIDENCE = 0.8f

    // 状态超时（毫秒）
    const val NAVIGATE_TIMEOUT = 30_000L
    const val MATCHING_TIMEOUT = 120_000L
    const val BATTLE_TIMEOUT = 600_000L
    const val IDLE_INTERVAL = 1500L

    // 连续失败上限
    const val MAX_FAILURES = 5

    // 模板名称
    val TEMPLATE_NAMES = listOf(
        "tab_2", "daily_dungeon", "match_btn",
        "ready_btn", "settlement_close", "confirm_btn", "again_btn"
    )

    // 模板文件路径（在内部存储中）
    const val TEMPLATE_DIR = "templates"
}
