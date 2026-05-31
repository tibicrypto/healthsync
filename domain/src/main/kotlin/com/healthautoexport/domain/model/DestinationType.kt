package com.healthautoexport.domain.model

/**
 * Các đích đến (Destination) mà một Export_Job có thể gửi payload tới (Requirements 16–21).
 */
enum class DestinationType {
    REST_API,
    GOOGLE_DRIVE,
    DROPBOX,
    MQTT,
    HOME_ASSISTANT,
    LOCAL_STORAGE,
}
