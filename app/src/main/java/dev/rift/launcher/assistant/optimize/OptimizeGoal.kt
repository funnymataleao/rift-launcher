package dev.rift.launcher.assistant.optimize

/** What the user asks the assistant to aim for. The wording goes to the model as is. */
enum class OptimizeGoal(val id: String, val instruction: String) {
    Balanced(
        "balanced",
        "Balanced: a steady 60 FPS (or a steady 40 on a heavy game) without making the picture noticeably worse.",
    ),
    MaxFps("max_fps", "Maximum FPS: performance first, a lower internal resolution and faster translator presets are fine."),
    Battery("battery", "Battery and heat: cap the frame rate and keep the device cool, a lower resolution is fine."),
    Quality("quality", "Picture: native resolution and sharpness first, as long as the game stays playable."),
    Stability(
        "stability",
        "Stability: the game crashes or does not start. Prefer compatible presets and combinations that community runs rated 5 used, " +
            "and read the Wine errors.",
    ),
    SafeFirstRun("safe_first_run", "Safe first run: only a combination the community has already proven on this GPU, no experiments."),
    ;

    companion object {
        fun byId(id: String?): OptimizeGoal = entries.firstOrNull { it.id == id } ?: Balanced
    }
}
