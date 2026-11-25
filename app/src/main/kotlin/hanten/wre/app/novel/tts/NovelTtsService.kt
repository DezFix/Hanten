package hanten.wre.app.novel.tts

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import hanten.wre.app.core.util.ext.printStackTraceDebug
import hanten.wre.app.R

/**
 * Hanten 3.0: reads a ranobe chapter aloud in the foreground (media playback), with a notification
 * player: pause / continue / stop. Survives the reader going to the background and the screen off.
 * Pause = stop the engine and remember the current chunk; continue starts that chunk again.
 */
class NovelTtsService : Service() {

	private val handler = Handler(Looper.getMainLooper())
	private var tts: TextToSpeech? = null
	private var isReady = false
	private var isPlayRequested = false

	private val sleepRunnable = Runnable { stopReading() }

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createChannel()
		tts = TextToSpeech(this) { status ->
			handler.post {
				isReady = status == TextToSpeech.SUCCESS
				if (!isReady) {
					Toast.makeText(this, R.string.novel_tts_unavailable, Toast.LENGTH_SHORT).show()
					stopReading()
				} else {
					tts?.setOnUtteranceProgressListener(progressListener)
					if (isPlayRequested) speakFromPosition()
				}
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_PLAY -> {
				goForeground(isSpeaking = true)
				isPlayRequested = true
				applyPendingSleepTimer()
				if (isReady) speakFromPosition()
			}

			ACTION_PAUSE -> pauseReading()
			ACTION_STOP -> stopReading()
			ACTION_SLEEP -> setSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
			else -> stopReading()
		}
		return START_NOT_STICKY
	}

	override fun onDestroy() {
		handler.removeCallbacksAndMessages(null)
		tts?.stop()
		tts?.shutdown()
		tts = null
		NovelTts.update { it.copy(status = NovelTts.Status.IDLE, sleepAtMillis = 0L) }
		super.onDestroy()
	}

	private fun speakFromPosition() {
		val engine = tts ?: return
		val chunks = NovelTts.chunks
		if (chunks.isEmpty() || NovelTts.position >= chunks.size) {
			stopReading()
			return
		}
		val availability = engine.isLanguageAvailable(NovelTts.language)
		if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
			Toast.makeText(this, R.string.novel_tts_unavailable, Toast.LENGTH_SHORT).show()
			stopReading()
			return
		}
		engine.language = NovelTts.language
		val from = NovelTts.position
		for (index in from until chunks.size) {
			engine.speak(
				chunks[index],
				if (index == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
				null,
				UTTERANCE_PREFIX + index,
			)
		}
		NovelTts.update { it.copy(status = NovelTts.Status.SPEAKING) }
		notifyState(isSpeaking = true)
	}

	private fun pauseReading() {
		isPlayRequested = false
		tts?.stop()
		NovelTts.update { it.copy(status = NovelTts.Status.PAUSED) }
		goForeground(isSpeaking = false)
	}

	private fun stopReading() {
		isPlayRequested = false
		handler.removeCallbacks(sleepRunnable)
		tts?.stop()
		NovelTts.position = 0
		NovelTts.update { it.copy(status = NovelTts.Status.IDLE, sleepAtMillis = 0L) }
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun setSleepTimer(minutes: Int) {
		handler.removeCallbacks(sleepRunnable)
		val at = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
		if (minutes > 0) handler.postDelayed(sleepRunnable, minutes * 60_000L)
		NovelTts.update { it.copy(sleepAtMillis = at) }
	}

	/** A timer chosen before anything was playing */
	private fun applyPendingSleepTimer() {
		val at = NovelTts.state.value.sleepAtMillis
		handler.removeCallbacks(sleepRunnable)
		if (at > System.currentTimeMillis()) {
			handler.postDelayed(sleepRunnable, at - System.currentTimeMillis())
		}
	}

	private val progressListener = object : UtteranceProgressListener() {

		override fun onStart(utteranceId: String?) {
			val index = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
			NovelTts.position = index
		}

		override fun onDone(utteranceId: String?) {
			val index = utteranceId?.removePrefix(UTTERANCE_PREFIX)?.toIntOrNull() ?: return
			if (index >= NovelTts.chunks.lastIndex) {
				handler.post { stopReading() }
			}
		}

		@Deprecated("Deprecated in Java")
		override fun onError(utteranceId: String?) {
			handler.post { stopReading() }
		}

		override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
	}

	private fun goForeground(isSpeaking: Boolean) {
		ServiceCompat.startForeground(
			this,
			NOTIFICATION_ID,
			buildNotification(isSpeaking),
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
			} else {
				0
			},
		)
	}

	private fun notifyState(isSpeaking: Boolean) {
		try {
			NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(isSpeaking))
		} catch (e: SecurityException) {
			// notifications permission was revoked: speech keeps going without the player update
			e.printStackTraceDebug()
		}
	}

	private fun buildNotification(isSpeaking: Boolean): Notification {
		val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
			PendingIntentCompat.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT, false)
		}
		val toggle = if (isSpeaking) {
			NotificationCompat.Action(
				R.drawable.ic_action_pause,
				getString(R.string.novel_tts_pause),
				actionIntent(ACTION_PAUSE, 1),
			)
		} else {
			NotificationCompat.Action(
				R.drawable.ic_play,
				getString(R.string.novel_tts_continue),
				actionIntent(ACTION_PLAY, 2),
			)
		}
		val stop = NotificationCompat.Action(
			R.drawable.ic_clear_all,
			getString(R.string.novel_tts_stop),
			actionIntent(ACTION_STOP, 3),
		)
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_play)
			.setContentTitle(NovelTts.state.value.title ?: getString(R.string.novel_tts))
			.setContentText(getString(if (isSpeaking) R.string.novel_tts_reading else R.string.novel_tts_paused))
			.setContentIntent(open)
			.setDeleteIntent(actionIntent(ACTION_STOP, 4))
			.addAction(toggle)
			.addAction(stop)
			.setOngoing(isSpeaking)
			.setOnlyAlertOnce(true)
			.setSilent(true)
			.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.build()
	}

	private fun actionIntent(action: String, requestCode: Int): PendingIntent? = PendingIntentCompat.getService(
		this,
		requestCode,
		Intent(this, NovelTtsService::class.java).setAction(action),
		PendingIntent.FLAG_UPDATE_CURRENT,
		false,
	)

	private fun createChannel() {
		val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
			.setName(getString(R.string.novel_tts_channel))
			.setShowBadge(false)
			.setVibrationEnabled(false)
			.setSound(null, null)
			.build()
		NotificationManagerCompat.from(this).createNotificationChannel(channel)
	}

	companion object {

		const val ACTION_PLAY = "hanten.wre.app.tts.PLAY"
		const val ACTION_PAUSE = "hanten.wre.app.tts.PAUSE"
		const val ACTION_STOP = "hanten.wre.app.tts.STOP"
		const val ACTION_SLEEP = "hanten.wre.app.tts.SLEEP"
		const val EXTRA_MINUTES = "minutes"

		private const val CHANNEL_ID = "novel_tts"
		private const val NOTIFICATION_ID = 0x7175
		private const val UTTERANCE_PREFIX = "novel-"
	}
}
