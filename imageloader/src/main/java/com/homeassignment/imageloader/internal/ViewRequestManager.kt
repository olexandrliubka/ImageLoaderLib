package com.homeassignment.imageloader.internal

import android.os.Looper
import android.view.View
import androidx.annotation.MainThread
import com.homeassignment.imageloader.Disposable
import com.homeassignment.imageloader.R
import kotlinx.coroutines.Job

/**
 * Tracks the request bound to one view: a new request cancels the previous one (e.g. when a
 * RecyclerView item is rebound), and a request still running when the view is detached from its
 * window is cancelled and restarted on re-attach.
 */
internal class ViewRequestManager : View.OnAttachStateChangeListener {
    private var currentJob: Job? = null
    private var restart: (() -> Unit)? = null
    private var restartOnAttach = false

    @MainThread
    fun setRequest(job: Job, restart: () -> Unit): Disposable {
        currentJob?.cancel()
        currentJob = job
        this.restart = restart
        restartOnAttach = false
        return ViewTargetDisposable(job, this)
    }

    @MainThread
    fun clear() {
        currentJob?.cancel()
        currentJob = null
        restart = null
        restartOnAttach = false
    }

    @MainThread
    fun dispose(job: Job) {
        if (currentJob === job) clear() else job.cancel()
    }

    override fun onViewDetachedFromWindow(v: View) {
        val job = currentJob ?: return
        if (job.isActive) {
            job.cancel()
            restartOnAttach = true
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        if (restartOnAttach) {
            restartOnAttach = false
            restart?.invoke()
        }
    }
}

private class ViewTargetDisposable(
    private val job: Job,
    private val manager: ViewRequestManager,
) : Disposable {
    override val isDisposed: Boolean get() = !job.isActive

    override fun dispose() {
        if (Looper.myLooper() == Looper.getMainLooper()) manager.dispose(job) else job.cancel()
    }
}

internal fun View.requestManagerOrNull(): ViewRequestManager? =
    getTag(R.id.imageloader_request_manager) as? ViewRequestManager

internal fun View.requestManager(): ViewRequestManager =
    requestManagerOrNull() ?: ViewRequestManager().also {
        setTag(R.id.imageloader_request_manager, it)
        addOnAttachStateChangeListener(it)
    }
