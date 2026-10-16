// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.model

/**
 * Where a game runs. The order of [priority] is the default choice of the Play button
 * when the user has not picked a source for the game: PC stream, emulator, this device, cloud.
 */
enum class LaunchSource(val priority: Int) {
    /** Streamed from the user's PC through the embedded Artemis core. */
    PC_STREAM(0),

    /** A ROM started in an installed emulator app. */
    EMULATOR(10),

    /** An Android game installed on this device. */
    NATIVE(20),

    /** A Windows game run on this device by the GameNative engine (Steam, Epic, GOG, Amazon). */
    GAMENATIVE(21),

    /** Xbox Cloud Gaming (Game Pass). */
    CLOUD_XCLOUD(30),

    /** GeForce NOW. */
    CLOUD_GFN(31),

    /** PlayStation or Xbox Remote Play from a console in the home network. */
    CONSOLE_REMOTE(40),
}
