package vn.name.leduyquang753.finerecorder;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioEncoder;
import android.media.MediaRecorder.AudioSource;
import android.media.MediaRecorder.OutputFormat;
import android.provider.MediaStore.Audio.Media;
import android.provider.MediaStore.MediaColumns;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.lang.System;
import kotlin.math.min;

class Recorder: Service() {
	companion object {
		const val MAX_LEVEL: Int = 32767;
	}
	
	private lateinit var mediaRecorder: MediaRecorder;
	private lateinit var parcelFileDescriptor: ParcelFileDescriptor;
	private var recording: Boolean = false;
	var fileName: String = ""; private set;
	var startTime: Long = 0; private set;
	var stopCallback: (() -> Unit)? = null;
	
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		fileName = "${System.currentTimeMillis()}.opus";
		startForeground(
			1,
			Notification.Builder(this, "vn.name.leduyquang753.finerecorder.notifications")
			.setOngoing(true)
			.setSmallIcon(R.drawable.baseline_mic_24)
			.setContentTitle("Recording...")
			.setContentText(fileName)
			.setTicker("An audio recording is in progress.")
			.setContentIntent(PendingIntent.getActivity(
				this, 0, packageManager.getLaunchIntentForPackage("vn.name.leduyquang753.finerecorder"),
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			))
			.addAction(Notification.Action.Builder(
				Icon.createWithResource(this, R.drawable.baseline_stop_24), "Stop",
				PendingIntent.getBroadcast(
					this, 0, Intent(this, StopRecordingBroadcastReceiver::class.java),
					PendingIntent.FLAG_IMMUTABLE
				)
			).build())
			.setWhen(System.currentTimeMillis())
			.setUsesChronometer(true)
			.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
			.build()
		);
		
		val contentValues = ContentValues();
		contentValues.put(MediaColumns.DISPLAY_NAME, fileName);
		contentValues.put(MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RECORDINGS);
		val contentResolver = this.contentResolver;
		parcelFileDescriptor = contentResolver.openFileDescriptor(
			contentResolver.insert(Media.EXTERNAL_CONTENT_URI, contentValues) ?: return START_NOT_STICKY, "w"
		) ?: return START_NOT_STICKY;
		
		try {
			@Suppress("DEPRECATION")
			mediaRecorder = MediaRecorder();
			mediaRecorder.setAudioSource(AudioSource.MIC);
			mediaRecorder.setOutputFormat(OutputFormat.OGG);
			mediaRecorder.setAudioEncoder(AudioEncoder.OPUS);
			mediaRecorder.setAudioSamplingRate(48000);
			mediaRecorder.setAudioEncodingBitRate(128000);
			mediaRecorder.setAudioChannels(1);
			mediaRecorder.setOutputFile(parcelFileDescriptor.fileDescriptor);
			mediaRecorder.prepare();
			mediaRecorder.start();
		} catch (e: IllegalStateException) {
			e.printStackTrace();
			parcelFileDescriptor.closeWithError("Failed to start recording.");
		}

		recording = true;
		startTime = SystemClock.elapsedRealtime();

		return START_NOT_STICKY;
	}

	override fun onBind(intent: Intent): IBinder = RecordingBinder(this);

	override fun onDestroy() {
		if (recording) stop();
	}

	fun stop() {
		val localStopCallback = stopCallback;
		if (localStopCallback != null) localStopCallback();
		recording = false;
		mediaRecorder.stop();
		mediaRecorder.release();
		parcelFileDescriptor.close();
		stopSelf();
	}

	val level: Int get() = if (recording) min(mediaRecorder.maxAmplitude, MAX_LEVEL) else 0;

	class RecordingBinder(val recorder: Recorder): Binder();

	class StopRecordingBroadcastReceiver: BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			(peekService(context, Intent(context, Recorder::class.java)) as RecordingBinder).recorder.stop();
		}
	}
}