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

    /** Kenji-NX (Switch): its own action and the game's URI in the bootPath extra. */
    KENJINX,

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
    /**
     * Names (lower case) that mark a folder as one game when it holds one of them, for systems whose games are
     * folders and not files (PS3 and PS4 dumps). A folder named like a game file ("Name.ps3") counts too.
     */
    val gameFolders: Set<String> = emptySet(),
)

/**
 * The systems offered in settings and the emulators that play them, following ES-DE's list of systems and
 * Android emulators. Emulators are looked up by package name and by name hints, and any other app on the
 * device can be picked for a system too (see EmulatorPicker), so one missing here still works.
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
        listOf("dev.eden.eden_emulator", "dev.legacy.eden_emulator", "dev.eden.eden_emulator.nightly", "com.miHoYo.Yuanshen"),
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
        "n64" to listOf(RetroCore("Mupen64Plus-Next", "mupen64plus_next_gles3"), RetroCore("ParaLLEl N64", "parallel_n64")),
        "gb" to listOf(RetroCore("Gambatte", "gambatte"), RetroCore("mGBA", "mgba"), RetroCore("SameBoy", "sameboy")),
        "virtualboy" to listOf(RetroCore("Beetle VB", "mednafen_vb")),
        "mastersystem" to listOf(RetroCore("Genesis Plus GX", "genesis_plus_gx"), RetroCore("SMS Plus GX", "smsplus"), RetroCore("PicoDrive", "picodrive")),
        "segacd" to listOf(RetroCore("Genesis Plus GX", "genesis_plus_gx"), RetroCore("PicoDrive", "picodrive")),
        "sega32x" to listOf(RetroCore("PicoDrive", "picodrive")),
        "saturn" to listOf(RetroCore("Beetle Saturn", "mednafen_saturn"), RetroCore("Yabause", "yabause")),
        "atari2600" to listOf(RetroCore("Stella", "stella")),
        "atari7800" to listOf(RetroCore("ProSystem", "prosystem")),
        "lynx" to listOf(RetroCore("Handy", "handy")),
        "jaguar" to listOf(RetroCore("Virtual Jaguar", "virtualjaguar")),
        "pcengine" to listOf(RetroCore("Beetle PCE Fast", "mednafen_pce_fast"), RetroCore("Beetle PCE", "mednafen_pce"), RetroCore("Beetle SuperGrafx", "mednafen_supergrafx")),
        "neogeo" to listOf(RetroCore("FinalBurn Neo", "fbneo")),
        "ngp" to listOf(RetroCore("Beetle NeoPop", "mednafen_ngp")),
        "wonderswan" to listOf(RetroCore("Beetle Cygne", "mednafen_wswan")),
        "arcade" to listOf(RetroCore("FinalBurn Neo", "fbneo"), RetroCore("MAME 2003-Plus", "mame2003_plus"), RetroCore("MAME", "mame")),
        "3do" to listOf(RetroCore("Opera", "opera")),
        "msx" to listOf(RetroCore("blueMSX", "bluemsx")),
        "c64" to listOf(RetroCore("VICE x64", "vice_x64")),
        "amiga" to listOf(RetroCore("PUAE", "puae")),
        "dos" to listOf(RetroCore("DOSBox Pure", "dosbox_pure")),
        "scummvm" to listOf(RetroCore("ScummVM", "scummvm")),
        "colecovision" to listOf(RetroCore("Gearcoleco", "gearcoleco"), RetroCore("blueMSX", "bluemsx")),
        "intellivision" to listOf(RetroCore("FreeIntv", "freeintv")),
        "pokemini" to listOf(RetroCore("PokeMini", "pokemini")),
    )

    /** A system that only RetroArch plays, with the cores in [retroCores] under the system's id. */
    private fun retroSystem(id: String, @StringRes nameRes: Int, extensions: Set<String>, noteRes: Int = 0) =
        PlatformDef(id, nameRes, extensions, listOf(retroArch), retroCores.getValue(id), noteRes)

    private val mupen = KnownEmulator(
        "M64Plus FZ",
        listOf("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita.amazon"),
        hints = listOf("mupen64plus"),
        download = EmulatorDownload(playId = "org.mupen64plusae.v3.fzurita"),
        activity = "paulscode.android.mupen64plusae.SplashActivity",
    )

    val platforms: List<PlatformDef> = listOf(
        PlatformDef(
            "switch", R.string.rift_platform_switch, setOf("nsp", "xci", "nca", "nso", "nro"),
            listOf(
                eden,
                KnownEmulator("Citron", listOf("org.citron.citron_emu"), LaunchKind.YUZU, hints = listOf("citron")),
                KnownEmulator("Sudachi", listOf("org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.ea"), LaunchKind.YUZU, hints = listOf("sudachi")),
                KnownEmulator("Yuzu", listOf("org.yuzu.yuzu_emu"), LaunchKind.YUZU),
                KnownEmulator("Suyu", listOf("dev.suyu.suyu_emu.relWithDebInfo"), LaunchKind.YUZU, hints = listOf("suyu")),
                KnownEmulator("Kenji-NX", listOf("org.kenjinx.android"), LaunchKind.KENJINX, hints = listOf("kenjinx")),
                KnownEmulator("Strato", listOf("org.stratoemu.strato"), LaunchKind.VIEW, hints = listOf("stratoemu")),
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
            "ps3", R.string.rift_platform_ps3, setOf("iso", "ps3"),
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
                KnownEmulator("RPCS3", listOf("net.rpcs3"), LaunchKind.VIEW, hints = listOf("rpcs3")),
                KnownEmulator("PS3Native", listOf("com.ps3native.standard"), LaunchKind.VIEW, hints = listOf("ps3native")),
            ),
            noteRes = R.string.rift_platform_ps3_note,
            gameFolders = setOf("ps3_disc.sfb", "ps3_game", "usrdir"),
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
            gameFolders = setOf("eboot.bin", "sce_sys"),
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
                KnownEmulator(
                    "Redream",
                    listOf("io.recompiled.redream"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "io.recompiled.redream"),
                    activity = "io.recompiled.redream.MainActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("dreamcast"),
        ),
        PlatformDef(
            "gba", R.string.rift_platform_gba, setOf("gba", "agb", "zip"),
            listOf(
                KnownEmulator(
                    "My Boy!",
                    listOf("com.fastemulator.gba"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "com.fastemulator.gba"),
                    activity = "com.fastemulator.gba.EmulatorActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("gba"),
        ),
        PlatformDef("snes", R.string.rift_platform_snes, setOf("sfc", "smc", "swc", "fig", "bs", "zip"), listOf(retroArch), retroCores.getValue("snes")),
        PlatformDef("nes", R.string.rift_platform_nes, setOf("nes", "fds", "unf", "unif", "zip"), listOf(retroArch), retroCores.getValue("nes")),
        PlatformDef("genesis", R.string.rift_platform_genesis, setOf("md", "gen", "smd", "bin", "zip"), listOf(retroArch), retroCores.getValue("genesis")),
        PlatformDef(
            "n64", R.string.rift_platform_n64, setOf("n64", "z64", "v64", "ndd", "zip", "7z"),
            listOf(mupen, retroArch), retroCores.getValue("n64"),
        ),
        PlatformDef(
            "gb", R.string.rift_platform_gb, setOf("gb", "sgb", "zip"),
            listOf(
                KnownEmulator(
                    "My OldBoy!",
                    listOf("com.fastemulator.gbc"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "com.fastemulator.gbc"),
                    activity = "com.fastemulator.gbc.EmulatorActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("gb"),
        ),
        PlatformDef(
            "gbc", R.string.rift_platform_gbc, setOf("gbc", "zip"),
            listOf(
                KnownEmulator(
                    "My OldBoy!",
                    listOf("com.fastemulator.gbc"),
                    LaunchKind.VIEW,
                    download = EmulatorDownload(playId = "com.fastemulator.gbc"),
                    activity = "com.fastemulator.gbc.EmulatorActivity",
                ),
                retroArch,
            ),
            retroCores.getValue("gb"),
        ),
        retroSystem("virtualboy", R.string.rift_platform_virtualboy, setOf("vb", "vboy", "zip")),
        PlatformDef(
            "wiiu", R.string.rift_platform_wiiu, setOf("wua", "wud", "wux", "rpx", "wup"),
            listOf(
                KnownEmulator(
                    "Cemu",
                    listOf("info.cemu.cemu"),
                    LaunchKind.VIEW,
                    hints = listOf("info.cemu"),
                    download = EmulatorDownload(url = "https://github.com/SSimco/Cemu/releases"),
                ),
            ),
        ),
        PlatformDef(
            "xbox360", R.string.rift_platform_xbox360, setOf("iso", "xex", "zar"),
            listOf(
                KnownEmulator("X360 Mobile", listOf("emu.x360.mobile"), LaunchKind.VIEW, hints = listOf("x360", "xenia", "ax360e")),
            ),
            noteRes = R.string.rift_platform_xbox360_note,
            gameFolders = setOf("default.xex"),
        ),
        retroSystem("mastersystem", R.string.rift_platform_mastersystem, setOf("sms", "sg", "zip")),
        retroSystem("gamegear", R.string.rift_platform_gamegear, setOf("gg", "zip")),
        retroSystem("segacd", R.string.rift_platform_segacd, setOf("cue", "chd", "iso", "bin")),
        retroSystem("sega32x", R.string.rift_platform_sega32x, setOf("32x", "zip", "bin")),
        PlatformDef(
            "saturn", R.string.rift_platform_saturn, setOf("chd", "cue", "iso", "mds", "ccd"),
            listOf(
                KnownEmulator(
                    "Yaba Sanshiro 2",
                    listOf("org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2"),
                    LaunchKind.VIEW,
                    hints = listOf("yabasanshiro"),
                    download = EmulatorDownload(playId = "org.devmiyax.yabasanshioro2"),
                    activity = "org.uoyabause.android.Yabause",
                ),
                retroArch,
            ),
            retroCores.getValue("saturn"),
        ),
        retroSystem("atari2600", R.string.rift_platform_atari2600, setOf("a26", "bin", "zip")),
        retroSystem("atari7800", R.string.rift_platform_atari7800, setOf("a78", "bin", "zip")),
        retroSystem("lynx", R.string.rift_platform_lynx, setOf("lnx", "zip")),
        retroSystem("jaguar", R.string.rift_platform_jaguar, setOf("j64", "jag", "zip")),
        retroSystem("pcengine", R.string.rift_platform_pcengine, setOf("pce", "sgx", "cue", "chd", "zip")),
        retroSystem("neogeo", R.string.rift_platform_neogeo, setOf("zip", "7z")),
        retroSystem("ngp", R.string.rift_platform_ngp, setOf("ngp", "ngc", "zip")),
        retroSystem("wonderswan", R.string.rift_platform_wonderswan, setOf("ws", "wsc", "zip")),
        retroSystem("arcade", R.string.rift_platform_arcade, setOf("zip", "7z", "chd")),
        retroSystem("3do", R.string.rift_platform_3do, setOf("iso", "cue", "chd")),
        retroSystem("msx", R.string.rift_platform_msx, setOf("rom", "mx1", "mx2", "dsk", "cas", "zip")),
        retroSystem("c64", R.string.rift_platform_c64, setOf("d64", "t64", "prg", "crt", "tap", "x64", "zip")),
        retroSystem("amiga", R.string.rift_platform_amiga, setOf("adf", "ipf", "lha", "hdf", "uae", "zip")),
        retroSystem("dos", R.string.rift_platform_dos, setOf("zip", "dosz", "iso", "cue", "conf")),
        retroSystem("scummvm", R.string.rift_platform_scummvm, setOf("scummvm", "svm")),
        retroSystem("colecovision", R.string.rift_platform_colecovision, setOf("col", "rom", "bin", "zip")),
        retroSystem("intellivision", R.string.rift_platform_intellivision, setOf("int", "bin", "rom", "zip")),
        retroSystem("pokemini", R.string.rift_platform_pokemini, setOf("min", "zip")),
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
