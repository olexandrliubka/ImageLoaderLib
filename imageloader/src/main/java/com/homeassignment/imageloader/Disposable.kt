package com.homeassignment.imageloader

/** Handle to an image request started with [RequestBuilder.into]. */
public interface Disposable {
    /** `true` once the request has completed or been cancelled. */
    public val isDisposed: Boolean

    /** Cancels the request if it is still running. */
    public fun dispose()
}
