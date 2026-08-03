package com.iris.alarm.domain.model

import kotlin.random.Random

/**
 * One thing the hunt can send you to find.
 *
 * [label] must be a string ML Kit's default image labeller can actually emit.
 * That model knows 447 labels and they are not the ones you would guess — there
 * is no "toothbrush", no "mug", no "book", no "towel", no "door". Every entry
 * here was checked against the label file inside the model asset, and
 * `HuntTargetVocabularyTest` re-checks them against a copy of that file so a
 * plausible-sounding addition cannot quietly become an impossible challenge.
 *
 * @param display what the user is told to find, in their words rather than the
 *   model's ("a kitchen counter", not "Countertop").
 * @param where a nudge toward the room, because the point is to walk somewhere.
 */
enum class HuntTarget(val label: String, val display: String, val where: String) {
    SINK("Sink", "a sink", "kitchen or bathroom"),
    CUP("Cup", "a cup or glass", "kitchen"),
    COUNTERTOP("Countertop", "a kitchen counter", "kitchen"),
    CABINETRY("Cabinetry", "a cupboard", "kitchen"),
    CUTLERY("Cutlery", "a fork, knife or spoon", "kitchen drawer"),
    SHOE("Shoe", "a shoe", "hallway or wardrobe"),
    JACKET("Jacket", "a jacket or coat", "hallway or wardrobe"),
    JEANS("Jeans", "a pair of jeans or trousers", "wardrobe"),
    CHAIR("Chair", "a chair", "anywhere but the bed"),
    COUCH("Couch", "a sofa", "living room"),
    TELEVISION("Television", "a TV or monitor", "living room or desk"),
    DESK("Desk", "a desk", "wherever you work"),
    SHELF("Shelf", "a shelf", "living room"),
    DRAWER("Drawer", "an open drawer", "anywhere with drawers"),
    CURTAIN("Curtain", "a curtain or blind", "any window"),
    PLANT("Plant", "a houseplant", "wherever it lives"),
    STAIRS("Stairs", "the stairs", "if you have them"),
    CLOCK("Clock", "a clock that is not this phone", "kitchen or wall"),
    ;

    companion object {
        /**
         * Picks a target the user has not just been shown.
         *
         * [exclude] is the set already offered this morning, so a swap always
         * produces something new — being handed "a sink" twice would make the
         * swap look broken. Falls back to the full pool once everything has been
         * excluded, which only happens if someone swaps more than there are
         * targets.
         */
        fun random(exclude: Set<HuntTarget> = emptySet(), random: Random = Random): HuntTarget {
            val pool = entries.filterNot { it in exclude }.ifEmpty { entries }
            return pool[random.nextInt(pool.size)]
        }
    }
}
