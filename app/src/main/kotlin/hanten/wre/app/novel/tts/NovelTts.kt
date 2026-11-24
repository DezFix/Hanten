package hanten.wre.app.novel.tts

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Hanten 3.0: reading a ranobe chapter aloud, owned by [NovelTtsService] so it keeps going with the
 * screen off or the app in the background. The reader only starts it and watches [state].
 */
object NovelTts {

	enum class Status { IDLE, SPEAKING, PAUSED }

	data class State(
		val status: Status = Status.IDLE,
		val title: String? = null,
		val sleepAtMillis: Long = 0L,
	)

	private val mutableState = MutableStateFlow(State())
	val state: StateFlow<State> = mutableState.asStateFlow()

	// What to read; kept here instead of the intent (a chapter can be bigger than a Binder transaction)
	@Volatile
	internal var chunks: List<String> = emptyList()
		private set

	@Volatile
	internal var language: Locale = Locale.getDefault()
		private set

	/** Index of the chunk being read; pause/resume restarts from its beginning */
	@Volatile
	internal var position: Int = 0

	fun start(context: Context, title: String, language: Locale, chunks: List<String>) {
		if (chunks.isEmpty()) return
		this.chunks = chunks
		this.language = language
		position = 0
		update { it.copy(title = title) }
		send(context, NovelTtsService.ACTION_PLAY)
	}

	fun pause(context: Context) = send(context, NovelTtsService.ACTION_PAUSE)

	fun resume(context: Context) = send(context, NovelTtsService.ACTION_PLAY)

	fun stop(context: Context) {
		if (state.value.status == Status.IDLE) return
		send(context, NovelTtsService.ACTION_STOP)
	}

	/** 0 = off */
	fun setSleepTimer(context: Context, minutes: Int) {
		val intent = Intent(context, NovelTtsService::class.java)
			.setAction(NovelTtsService.ACTION_SLEEP)
			.putExtra(NovelTtsService.EXTRA_MINUTES, minutes)
		if (state.value.status == Status.IDLE) {
			// nothing is playing yet: just remember it, the service applies it when it starts
			update { it.copy(sleepAtMillis = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L) }
			return
		}
		runCatching { context.startService(intent) }
	}

	internal fun update(block: (State) -> State) {
		mutableState.value = block(mutableState.value)
	}

	private fun send(context: Context, action: String) {
		val intent = Intent(context, NovelTtsService::class.java).setAction(action)
		if (action == NovelTtsService.ACTION_PLAY) {
			ContextCompat.startForegroundService(context, intent)
		} else {
			// the service is already running in the foreground; a plain start is enough (and never
			// demands another startForeground within 5 s)
			runCatching { context.startService(intent) }
		}
	}
}
