package com.leondeklerk.starling.edit.draw

/**
 * Define the different types of actions that can be performed on the layers.
 * Used for undo and redo operations.
 */
enum class ActionType {
    ADD,
    TRANSLATE,
    SCALE,
    DELETE,
    EDIT
}
