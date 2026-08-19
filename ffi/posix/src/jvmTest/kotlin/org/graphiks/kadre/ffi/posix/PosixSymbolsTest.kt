package org.graphiks.kadre.ffi.posix

import kotlin.test.Test
import kotlin.test.assertNotNull

class PosixSymbolsTest {
    @Test
    fun closeResolvesOnLinuxWithoutAssumingASoname() {
        if (System.getProperty("os.name") != "Linux") return

        assertNotNull(PosixSymbols.find("close"))
    }
}
