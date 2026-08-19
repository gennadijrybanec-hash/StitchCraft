package com.stitchcraft.app

object ReleaseConfig {
    const val APP_NAME = "StitchCraft"
    const val PRO_PRODUCT_ID = "stitchcraft_pro_lifetime"

    // Keep networking disabled by default until analytics/cloud features are explicitly added.
    const val ENABLE_ANALYTICS = false
    const val ENABLE_CLOUD_BACKUP = false
    const val ENABLE_CRASH_REPORTING = false

    const val FREE_MAX_WIDTH = 80
    const val FREE_MAX_HEIGHT = 80
    const val FREE_MAX_COLORS = 24

    const val PRO_MAX_WIDTH = 300
    const val PRO_MAX_HEIGHT = 300
    const val PRO_MAX_COLORS = 200
}
