package dev.rift.launcher.settings

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R

/** An emulator app Rift knows how to offer for a platform. */
internal data class KnownEmulator(val label: String, val packageName: String)

/** A game system the user can add as a platform, with the emulator apps that commonly play it. */
internal data class PlatformDef(
    val id: String,
    @StringRes val nameRes: Int,
    val emulators: List<KnownEmulator>,
)

/**
 * The platforms offered in settings. This is a starting list; the full database comes from ES-DE later.
 * Package names that are not installed are simply never shown.
 */
internal object EmulatorCatalog {
    private val retroArch = listOf(
        KnownEmulator("RetroArch", "com.retroarch"),
        KnownEmulator("RetroArch (64-bit)", "com.retroarch.aarch64"),
    )

    val platforms: List<PlatformDef> = listOf(
        PlatformDef(
            "switch", R.string.rift_platform_switch,
            listOf(
                KnownEmulator("Eden", "dev.eden.eden_emulator"),
                KnownEmulator("Citron", "org.citron.citron_emu"),
                KnownEmulator("Sudachi", "org.sudachi.sudachi_emu"),
                KnownEmulator("Yuzu", "org.yuzu.yuzu_emu"),
            ),
        ),
        PlatformDef("ps2", R.string.rift_platform_ps2, listOf(KnownEmulator("NetherSX2 / AetherSX2", "xyz.aethersx2.android"))),
        PlatformDef(
            "ps1", R.string.rift_platform_ps1,
            listOf(KnownEmulator("DuckStation", "com.github.stenzek.duckstation")) + retroArch,
        ),
        PlatformDef(
            "psp", R.string.rift_platform_psp,
            listOf(KnownEmulator("PPSSPP", "org.ppsspp.ppsspp"), KnownEmulator("PPSSPP Gold", "org.ppsspp.ppssppgold")) + retroArch,
        ),
        PlatformDef("vita", R.string.rift_platform_vita, listOf(KnownEmulator("Vita3K", "org.vita3k.emulator"))),
        PlatformDef(
            "3ds", R.string.rift_platform_3ds,
            listOf(
                KnownEmulator("Azahar", "org.azahar_emu.azahar"),
                KnownEmulator("Lime3DS", "io.github.lime3ds.android"),
                KnownEmulator("Citra", "org.citra.citra_emu"),
            ),
        ),
        PlatformDef(
            "nds", R.string.rift_platform_nds,
            listOf(KnownEmulator("melonDS", "me.magnum.melonds"), KnownEmulator("DraStic", "com.dsemu.drastic")) + retroArch,
        ),
        PlatformDef(
            "gc_wii", R.string.rift_platform_gc_wii,
            listOf(KnownEmulator("Dolphin", "org.dolphinemu.dolphinemu")),
        ),
        PlatformDef("dreamcast", R.string.rift_platform_dreamcast, listOf(KnownEmulator("Flycast", "com.flycast.emulator")) + retroArch),
        PlatformDef("gba", R.string.rift_platform_gba, retroArch),
        PlatformDef("snes", R.string.rift_platform_snes, retroArch),
        PlatformDef("nes", R.string.rift_platform_nes, retroArch),
        PlatformDef("genesis", R.string.rift_platform_genesis, retroArch),
    )

    fun platform(id: String): PlatformDef? = platforms.firstOrNull { it.id == id }

    /** The emulators of [platform] that are installed on this device. */
    fun installed(context: Context, platform: PlatformDef): List<KnownEmulator> =
        platform.emulators.filter { isInstalled(context, it.packageName) }

    fun labelFor(platform: PlatformDef?, packageName: String): String =
        platform?.emulators?.firstOrNull { it.packageName == packageName }?.label ?: packageName

    @Suppress("DEPRECATION")
    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
