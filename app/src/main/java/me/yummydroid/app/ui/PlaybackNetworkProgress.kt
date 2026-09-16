package me.yummydroid.app.ui

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicLong

/** Actual bytes, rather than completed media chunks, prove that a slow load is advancing. */
internal class PlaybackNetworkProgress : TransferListener {
    private val bytes = AtomicLong()
    val receivedBytes: Long get() = bytes.get()

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork && bytesTransferred > 0) bytes.addAndGet(bytesTransferred.toLong())
    }
}
