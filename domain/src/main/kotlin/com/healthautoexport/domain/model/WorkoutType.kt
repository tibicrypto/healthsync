package com.healthautoexport.domain.model

/**
 * Các loại Workout được hỗ trợ để người dùng chọn cho một Export_Job (Requirement 5.7).
 *
 * Đây là tập đại diện các loại phổ biến; [OTHER] dùng cho mọi loại Workout do Data_Source
 * cung cấp nhưng chưa có hằng số riêng, để không loại bỏ phiên tập.
 */
enum class WorkoutType {
    RUNNING,
    WALKING,
    CYCLING,
    SWIMMING,
    HIKING,
    STRENGTH_TRAINING,
    YOGA,
    HIIT,
    ROWING,
    ELLIPTICAL,
    PILATES,
    DANCE,
    BOXING,
    SKIING,
    SNOWBOARDING,
    TENNIS,
    BASKETBALL,
    SOCCER,
    GOLF,
    STAIR_CLIMBING,

    /** Loại Workout không nằm trong tập trên — giữ lại phiên tập với nhãn chung. */
    OTHER,
}
