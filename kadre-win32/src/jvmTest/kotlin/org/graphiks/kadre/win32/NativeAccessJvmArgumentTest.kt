package org.graphiks.kadre.win32

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeAccessJvmArgumentTest {
    @Test
    fun `Win32 backend test JVM enables unnamed module native access exactly once`() {
        val nativeAccessArguments = ManagementFactory.getRuntimeMXBean().inputArguments
            .filter { it == "--enable-native-access=ALL-UNNAMED" }

        assertEquals(1, nativeAccessArguments.size)
    }
}
