@file:Suppress("UnusedReceiverParameter")

package hanten.wre.app.core.util.ext

@Suppress("NOTHING_TO_INLINE")
inline fun Throwable.printStackTraceDebug() = Unit

fun Throwable.printStackTraceDebug(tag: String) = Unit

fun Throwable.printStackTraceDebug(tag: String, source: String) = Unit
fun assertNotInMainThread() = Unit
