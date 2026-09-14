package org.graphiks.kadre.samples.desktour.appkit

internal enum class ComposeMountState { New, Mounted, Closing, Closed, Failed }

internal class ComposeMountLifecycle {
    var state: ComposeMountState = ComposeMountState.New
        private set

    fun markMounted() {
        if (state == ComposeMountState.New) state = ComposeMountState.Mounted
    }

    fun beginClose() {
        if (state == ComposeMountState.New || state == ComposeMountState.Mounted) {
            state = ComposeMountState.Closing
        }
    }

    fun markClosed() {
        if (state == ComposeMountState.Closing) state = ComposeMountState.Closed
    }

    fun markFailed() {
        if (state == ComposeMountState.New) state = ComposeMountState.Failed
    }
}
