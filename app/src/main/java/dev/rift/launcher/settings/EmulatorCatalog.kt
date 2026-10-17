package dev.rift.launcher.settings

import android.content.Context
import androidx.annotation.StringRes
import app.gamenative.R

/** How Rift hands a game to an emulator app (the intents come from ES-DE's open find rules, see research/04-emulators.md). */
internal enum class LaunchKind {
    /** ACTION_VIEW with the game as data, sent to the emulator's package. Most emulators take this. */
    VIEW,

    /** yuzu family (Eden, Citron, Sudachi, Yuzu): TECH_DISCOVERED to EmulationActivity. */
    YUZU,

    /** Citra family (Azahar, Lime3DS, Citra): ACTION_VIEW to EmulationActivity. */
    CITRA,

    /** RetroArch: needs the game's real path and the libretro core to run it with. */
    RETROARCH,

    /** DuckStation: the game's URI goes in the bootPath extra. */
    DUCKSTATION,

    /** NetherSX2 / AetherSX2 / ARMSX2-style: ACTION_MAIN with the bootPath extra. */
    AETHERSX2,

    MELONDS,
    DOLPHIN,

    /** Vita3K: the game is a .psvita file holding the title ID. */
    VITA3K,

    /** aPS3e: its own action and the disc image's URI in the iso_uri extra. */
    APS3E,

    /** ARMSX3: the game goes in the path extra of its main activity. */
    ARMSX3,

    /** EmuCoreC: the game goes in the gamePath extra of its PS3 activity. */
    EMUCOREC,

    /** BachataS4 (PS4): starts an installed game by its CUSA code, taken from the file name. */
    BACHATAS4,
}

/** Where to get an emulator from: only its own official page or its Google Play listing. */
internal data class EmulatorDownload(val playId: String? = null, val url: String? = null)

/**
 * An emulator app Rift knows. [packages] are the exact package names (builds from Google Play, GitHub and
 * nightlies differ); [hints] catch other builds by a part of the package name, so a renamed or
 * unusual build of the same emulator is still found.
 */
internal data class KnownEmulator(
    val label: String,
    val packages: List<String>,
    val kind: LaunchKind = LaunchKind.VIEW,
    val hints: List<String> = emptyList(),
    val download: EmulatorDownload? = null,
    /** Full class name of the activity that opens a game, for [LaunchKind.VIEW] emulators that do not take the open-with intent. */
    val activity: String? = null,
) {
    fun matches(packageName: String): Boolean {
        if (packageName in packages) return true
        val lower = packageName.lowercase()
        return hints.any { lower.contains(it) }
    }
}

/** A libretro core RetroArch can run a system with: [file] is the name before "_libretro_android.so". */
internal data class RetroCore(val label: String, val file: String)

/** A game system the user can add as a platform, with the emulator apps that commonly play it. */
internal data class PlatformDef(
    val id: String,
    @StringRes val nameRes: Int,
    val extensions: Set<String>,
    val emulators: List<KnownEmulator>,
    /** Cores to offer when the chosen emulator is RetroArch; the first one is the default. */
    val cores: List<RetroCore> = emptyList(),
    /** A note shown on the emulator page, for limits the person should know before choosing. */
    @StringRes val noteRes: Int = 0,
)

/**
 * The systems offered in settings and the emulators that play them. This is a starting list; the full
 * database comes from ES-DE later. Emulators are looked up by package name and by name hints, and any
 * other app on the device can be picked for a system too (see EmulatorPicker).
 */
internal object EmulatorCatalog {
    private val retroArch = KnownEmulator(
        label = "RetroArch",
        packages = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32"),
        kind = LaunchKind.RETROARCH,
        hints = listOf("retroarch"),
        download = EmulatorDownload(playId = "com.retroarch"),
    )

    private val eden = KnownEmulator(
        "Eden",
        listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator", "dev.eden.eden_emulator.nightly"),
        LaunchKind.YUZU,
        hints = listOf("eden_emulator"),
        download = EmulatorDownload(url = "https://git.eden-emu.dev/eden-emu/eden/releases"),
    )

    private val retroCores = mapOf(
        "ps1" to listOf(RetroCore("SwanStation", "swanstation"), RetroCore("PCSX ReARMed", "pcsx_rearmed"), RetroCore("Beetle PSX", "mednafen_psx")),
        "psp" to listOf(RetroCore("PPSSPP", "ppsspp")),
        "nds" to listOf(RetroCore("melonDS", "melonds"), RetroCore("DeSmuME", "desmume")),
        "dreamcast" to listOf(RetroCore("Flycast", "flycast")),
        "gba" to listOf(RetroCore("mGBA", "mgba"), RetroCore("gpSP", "gpsp")),
        "snes" to listOf(RetroCore("Snes9x", "snes9x"), RetroCore("bsnes", "bsnes")),
        "nes" to listOf(RetroCore("FCEUmm", "fceumm"), RetroCore("Nestopia UE", "nestopia")),
        "genesis" to listOf(RetroCore("Genesis Plus GX", "genesis_plus_gx"), RetroCore("PicoDrive", "picodrive")),
    )

    val platforms: List<PlatformDef> = listOf(
        PlatformDef(
            "switch", R.string.rift_platform_switch, setOf("nsp", "xci", "nca", "nso", "nro"),
            listOf(
                eden,
                KnownEmulator("Citron", listOf("org.citron.citron_emu"), LaunchKind.YUZU, hints = listOf("citron")),
                KnownEmulator("Sudachi", listOf("org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.ea"), LaunchKind.YUZU, hints = listOf("sudachi")),
                KnownEmulator("Yuzu", listOf("org.yuzu.yuzu_emu"), LaunchKind.YUZU),
            ),
        ),
        PlatformDef(
            "ps2", R.string.rift_platform_ps2, setOf("iso", "bin", "cue", "chd", "cso", "gz", "mdf", "img", "zso"),
            listOf(
                KnownEmulator(
                    "NetherSX2 / AetherSX2",
                    listOf("xyz.aethersx2.android", "xyz.aethersx2.tturnip", "xyz.aethersx2.cturnip"),
                    LaunchKind.AETHERSX2,
                    download = EmulatorDownload(url = "https://github.com/Trixarian/NetherSX2-patch"),
                ),
                KnownEmulator(
                    "ARMSX2",
                    listOf("come.nanodata.armsx2", "com.armsx2", "com.armsx2.nightly"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "come.nanodata.armsx2"),
                ),
                KnownEmulator("Play!", listOf("com.virtualapplications.play"), LaunchKind.VIEW, activity = "com.virtualapplications.play.MainActivity"),
            ),
        ),
        PlatformDef(
            "ps3", R.string.rift_platform_ps3, setOf("iso"),
            listOf(
                KnownEmulator(
                    "aPS3e",
                    listOf("aenu.aps3e", "aenu.aps3e.premium"),
                    LaunchKind.APS3E,
                    hints = listOf("aps3e"),
                    download = EmulatorDownload(playId = "aenu.aps3e"),
                ),
                KnownEmulator(
                    "ARMSX3",
                    listOf("com.armsx3"),
                    LaunchKind.ARMSX3,
                    hints = listOf("armsx3"),
                    download = EmulatorDownload(url = "https://github.com/ARMSX2/ARMSX3/releases"),
                ),
                KnownEmulator("EmuCoreC", listOf("com.sbro.emucorec"), LaunchKind.EMUCOREC, hints = listOf("emucorec")),
            ),
            noteRes = R.string.rift_platform_ps3_note,
        ),
        PlatformDef(
            "ps4", R.string.rift_platform_ps4, setOf("pkg", "ps4"),
            listOf(
                KnownEmulator(
                    "Bachata S4",
                    listOf("com.bachatas4.android", "com.bachatas4.android.github"),
                    LaunchKind.BACHATAS4,
                    hints = listOf("bachatas4"),
                    download = EmulatorDownload(url = "https://github.com/JICA98/Bachata-S4/releases"),
                ),
            ),
            noteRes = R.string.rift_platform_ps4_note,
        ),
        PlatformDef(
            "ps1", R.string.rift_platform_ps1, setOf("bin", "cue", "chd", "iso", "img", "pbp", "m3u", "mdf"),
            listOf(
                KnownEmulator(
                    "DuckStation",
                    listOf("com.github.stenzek.duckstation"),
                    LaunchKind.DUCKSTATION,
                    download = EmulatorDownload(url = "https://github.com/stenzek/duckstation/releases"),
                ),
                retroArch,
            ),
            retroCores.getValue("ps1"),
        ),
        PlatformDef(
            "psp", R.string.rift_platform_psp, setOf("iso", "cso", "pbp", "chd", "elf"),
            listOf(
                KnownEmulator(
                    "PPSSPP",
                    listOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "org.ppsspp.ppsspp"),
                    activity = "org.ppsspp.ppsspp.PpssppActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("psp"),
        ),
        PlatformDef(
            "vita", R.string.rift_platform_vita, setOf("psvita"),
            listOf(
                KnownEmulator(
                    "Vita3K",
                    listOf("org.vita3k.emulator", "org.vita3k.emulator.ikhoeyZX"),
                    LaunchKind.VITA3K,
                    download = EmulatorDownload(url = "https://github.com/Vita3K/Vita3K/releases"),
                ),
            ),
        ),
        PlatformDef(
            "3ds", R.string.rift_platform_3ds, setOf("3ds", "3dsx", "cci", "cia", "cxi", "app"),
            listOf(
                KnownEmulator(
                    "Azahar",
                    listOf("org.azahar_emu.azahar"),
                    LaunchKind.CITRA,
                    hints = listOf("azahar"),
                    download = EmulatorDownload(url = "https://github.com/azahar-emu/azahar/releases"),
                ),
                KnownEmulator(
                    "Lime3DS",
                    listOf("io.github.lime3ds.android"),
                    LaunchKind.CITRA,
                    hints = listOf("lime3ds"),
                    download = EmulatorDownload(playId = "io.github.lime3ds.android"),
                ),
                KnownEmulator("Citra", listOf("org.citra.citra_emu"), LaunchKind.CITRA),
            ),
        ),
        PlatformDef(
            "nds", R.string.rift_platform_nds, setOf("nds", "dsi", "srl"),
            listOf(
                KnownEmulator(
                    "melonDS",
                    listOf("me.magnum.melonds", "me.magnum.melonds.nightly"),
                    LaunchKind.MELONDS,
                    download = EmulatorDownload(playId = "me.magnum.melonds"),
                ),
                KnownEmulator("DraStic", listOf("com.dsemu.drastic"), LaunchKind.VIEW, activity = "com.dsemu.drastic.DraSticActivity"),
                retroArch,
            ),
            retroCores.getValue("nds"),
        ),
        PlatformDef(
            "gc_wii", R.string.rift_platform_gc_wii, setOf("iso", "gcm", "rvz", "wbfs", "ciso", "gcz", "wad", "dol"),
            listOf(
                KnownEmulator(
                    "Dolphin",
                    listOf("org.dolphinemu.dolphinemu"),
                    LaunchKind.DOLPHIN,
                    hints = listOf("dolphinemu"),
                    download = EmulatorDownload(url = "https://dolphin-emu.org/download/"),
                ),
            ),
        ),
        PlatformDef(
            "dreamcast", R.string.rift_platform_dreamcast, setOf("cdi", "gdi", "chd", "cue", "m3u"),
            listOf(
                KnownEmulator(
                    "Flycast",
                    listOf("com.flycast.emulator"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "com.flycast.emulator"),
                    activity = "com.flycast.emulator.MainActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("dreamcast"),
        ),
        PlatformDef("gba", R.string.rift_platform_gba, setOf("gba", "agb", "zip"), listOf(retroArch), retroCores.getValue("gba")),
        PlatformDef("snes", R.string.rift_platform_snes, setOf("sfc", "smc", "swc", "fig", "bs", "zip"), listOf(retroArch), retroCores.getValue("snes")),
        PlatformDef("nes", R.string.rift_platform_nes, setOf("nes", "fds", "unf", "unif", "zip"), listOf(retroArch), retroCores.getValue("nes")),
        PlatformDef("genesis", R.string.rift_platform_genesis, setOf("md", "gen", "smd", "bin", "zip"), listOf(retroArch), retroCores.getValue("genesis")),
    )

    fun platform(id: String): PlatformDef? = platforms.firstOrNull { it.id == id }

    /** The known emulator of [platform] that [packageName] is, if any. */
    fun known(platform: PlatformDef?, packageName: String): KnownEmulator? =
        platform?.emulators?.firstOrNull { it.matches(packageName) }

    /**
     * The installed package of [emulator], or null. The exact package names come first, then any installed
     * app whose package name carries one of the emulator's hints.
     */
    fun installedPackage(emulator: KnownEmulator, installed: Collection<String>): String? =
        emulator.packages.firstOrNull { it in installed } ?: installed.firstOrNull { emulator.matches(it) }

    /** How a game of [platform] is started with [packageName]: the known emulator's way, or the system's usual one. */
    fun launchKind(platform: PlatformDef?, packageName: String): LaunchKind {
        known(platform, packageName)?.let { return it.kind }
        if (packageName.lowercase().contains("retroarch")) return LaunchKind.RETROARCH
        // An app Rift does not know: a Switch app is taken for a yuzu family one, anything else gets the plain open-with.
        return if (platform?.id == "switch") LaunchKind.YUZU else LaunchKind.VIEW
    }

    /** The app's own name from the system, or the known emulator's name, or the package name. */
    fun labelFor(context: Context, packageName: String, platform: PlatformDef? = null): String =
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
            ?: known(platform, packageName)?.label
            ?: packageName

    /** The core file name RetroArch runs [platform] with: the one the user chose, or the system's first. */
    fun coreFile(platform: PlatformDef?, chosen: String): String? =
        platform?.cores?.firstOrNull { it.file == chosen }?.file ?: platform?.cores?.firstOrNull()?.file
}
