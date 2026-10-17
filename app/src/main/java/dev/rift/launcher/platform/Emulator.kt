package dev.rift.launcher.platform

import android.os.Build

/**
 * Detects the Android emulator (AVD).
 *
 * GameNative's native Vulkan probes crash the emulator's GPU stack (SIGSEGV in
 * vkGetPhysicalDeviceProperties), so they are skipped there. The emulator is only used to look at
 * the UI; games are not expected to run on it.
 */
object Emulator {
    @JvmStatic
    val isEmulator: Boolean by lazy {
        val hardware = Build.HARDWARE.lowercase()
        hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.FINGERPRINT.startsWith("generic") ||
            systemProperty("ro.kernel.qemu") == "1" ||
            systemProperty("ro.boot.qemu") == "1"
    }

    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull()
}
