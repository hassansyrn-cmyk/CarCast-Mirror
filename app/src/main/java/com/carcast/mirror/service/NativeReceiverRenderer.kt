package com.carcast.mirror.service

import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

object NativeReceiverRenderer {
    @Volatile private var renderer: SurfaceViewRenderer? = null
    @Volatile private var track: VideoTrack? = null
    private var egl: EglBase? = null

    @Synchronized fun attach(next: SurfaceViewRenderer) {
        renderer?.let { old -> track?.removeSink(old); runCatching { old.release() } }
        egl?.release()
        egl = EglBase.create()
        next.init(egl!!.eglBaseContext, null)
        next.setEnableHardwareScaler(true)
        next.setMirror(false)
        renderer = next
        track?.addSink(next)
    }

    @Synchronized fun setTrack(next: VideoTrack) {
        track?.let { old -> renderer?.let(old::removeSink) }
        track = next
        renderer?.let(next::addSink)
    }

    @Synchronized fun clearTrack() {
        track?.let { old -> renderer?.let(old::removeSink) }
        track = null
    }

    @Synchronized fun detach(view: SurfaceViewRenderer) {
        track?.removeSink(view)
        if (renderer === view) renderer = null
        runCatching { view.release() }
    }
}
