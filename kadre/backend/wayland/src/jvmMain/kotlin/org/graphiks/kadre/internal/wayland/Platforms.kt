package org.graphiks.kadre.internal.wayland

internal fun isLinux(): Boolean = System.getProperty("os.name", "").contains("Linux", ignoreCase = true)
