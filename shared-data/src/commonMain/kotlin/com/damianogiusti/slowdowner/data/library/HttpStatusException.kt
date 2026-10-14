package com.damianogiusti.slowdowner.data.library

import java.io.IOException

/**
 * The server answered, with an error. Still an IOException for callers that only care that the
 * download failed, but not a network problem: a 403 from YouTube means the stream was refused.
 */
class HttpStatusException(val status: Int) : IOException("HTTP $status while downloading")
