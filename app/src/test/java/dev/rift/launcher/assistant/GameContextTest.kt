package dev.rift.launcher.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class GameContextTest {

    @Test
    fun keepsWineErrorLinesOnceNewestLast() {
        val log = """
            0024:fixme:ntdll:NtQuerySystemInformation info_class SYSTEM_PERFORMANCE_INFORMATION
            0024:err:module:import_dll Library d3dcompiler_47.dll (which is needed by L"C:\\game\\game.exe") not found
            0030:trace:seh:dispatch_exception code=80000003
            wine: Unhandled exception 0xc0000005 in thread 24 at address 0x140001000
            0028:err:module:import_dll Library d3dcompiler_47.dll (which is needed by L"C:\\game\\game.exe") not found
        """.trimIndent()
        val errors = GameContextCollector.wineErrors(log.lineSequence())
        assertEquals(2, errors.size)
        assertEquals("wine: Unhandled exception 0xc0000005 in thread 24 at address 0x140001000", errors[0])
        assertEquals(true, errors[1].startsWith("0028:err:module:import_dll"))
    }
}
