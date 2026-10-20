package dev.rift.launcher.platform

import app.gamenative.data.EpicGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PublisherLauncherSetupTest {
    @Test
    fun epicMetadataDeterminesPublisher() {
        assertEquals(
            PublisherLauncherSetup.Publisher.UBISOFT,
            PublisherLauncherSetup.publisherForEpic(
                EpicGame(appName = "Unlisted", thirdPartyManagedApp = "UbisoftConnect"),
            ),
        )
        assertEquals(
            PublisherLauncherSetup.Publisher.EA,
            PublisherLauncherSetup.publisherForEpic(
                EpicGame(appName = "Other", thirdPartyManagedApp = "The EA App"),
            ),
        )
        assertEquals(
            PublisherLauncherSetup.Publisher.ROCKSTAR,
            PublisherLauncherSetup.publisherForEpic(
                EpicGame(appName = "Another", thirdPartyManagedApp = "Rockstar Games Launcher"),
            ),
        )
    }

    @Test
    fun syndicateIsRecognizedButUnrelatedGamesAreNot() {
        assertEquals(
            PublisherLauncherSetup.Publisher.UBISOFT,
            PublisherLauncherSetup.publisherForEpic(EpicGame(appName = "Albacore")),
        )
        assertNull(PublisherLauncherSetup.publisherForEpic(EpicGame(appName = "HogwartsLegacy")))
    }

    @Test
    fun corruptedOrIncompleteInstallerIsRejected() {
        val dir = createTempDirectory("publisher-installer-test").toFile()
        try {
            val installer = File(dir, "UbisoftConnectInstaller.exe")
            assertFalse(PublisherLauncherSetup.isPlausibleInstaller(installer))
            installer.writeText("MZ")
            assertFalse(PublisherLauncherSetup.isPlausibleInstaller(installer))
            installer.outputStream().use {
                it.write(byteArrayOf('B'.code.toByte(), 'A'.code.toByte()))
                it.write(ByteArray(1_048_576))
            }
            assertFalse(PublisherLauncherSetup.isPlausibleInstaller(installer))
            installer.outputStream().use {
                it.write(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte()))
                it.write(ByteArray(1_048_576))
            }
            assertTrue(PublisherLauncherSetup.isPlausibleInstaller(installer))
        } finally {
            dir.deleteRecursively()
        }
    }
}
