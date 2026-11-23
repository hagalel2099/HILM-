package com.nossouniverso.launcher.model

enum class SlotPosition(val id: String, val isPlate: Boolean) {
    PLATE_TOP_LEFT("plate_top_left", true),
    PLATE_TOP_RIGHT("plate_top_right", true),
    PLATE_BOTTOM_LEFT("plate_bottom_left", true),
    PLATE_BOTTOM_RIGHT("plate_bottom_right", true),
    DOCK_1("dock_1", false),
    DOCK_2("dock_2", false),
    DOCK_4("dock_4", false),
    DOCK_5("dock_5", false),
    LEFT_1("left_1", false),
    LEFT_2("left_2", false),
    LEFT_3("left_3", false),
    LEFT_4("left_4", false),
    LEFT_5("left_5", false),
    RIGHT_1("right_1", false),
    RIGHT_2("right_2", false),
    RIGHT_3("right_3", false),
    RIGHT_4("right_4", false),
    RIGHT_5("right_5", false);

    val isLeftDrawer: Boolean
        get() = name.startsWith("LEFT_")

    val isRightDrawer: Boolean
        get() = name.startsWith("RIGHT_")

    companion object {
        fun fromId(id: String): SlotPosition? = values().firstOrNull { it.id == id }
    }
}

data class ShortcutSlot(
    val position: SlotPosition,
    val packageName: String? = null,
    val activityName: String? = null,
    val customLabel: String? = null,
    val customIconUri: String? = null
) {
    val isEmpty: Boolean
        get() = packageName.isNullOrBlank()
}
