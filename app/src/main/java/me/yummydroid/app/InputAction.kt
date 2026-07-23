package me.yummydroid.app

enum class InputAction {
    Up,
    Down,
    Left,
    Right,
    Confirm,
    Play,
    Pause,
    PlayPause,
    PreviousEpisode,
    NextEpisode,
    Back,
}

data class InputActionEvent(
    val action: InputAction,
    val repeatCount: Int = 0,
    val followsPointerInput: Boolean = false,
) {
    val isRepeated: Boolean
        get() = repeatCount > 0
}
