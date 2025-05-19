@file:OptIn(ExperimentalMaterial3Api::class)

package vn.name.leduyquang753.finerecorder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.activity.ComponentActivity;
import androidx.activity.compose.setContent;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.compose.foundation.background;
import androidx.compose.foundation.layout.Box;
import androidx.compose.foundation.layout.Column;
import androidx.compose.foundation.layout.Row;
import androidx.compose.foundation.layout.Spacer;
import androidx.compose.foundation.layout.fillMaxHeight;
import androidx.compose.foundation.layout.fillMaxSize;
import androidx.compose.foundation.layout.fillMaxWidth;
import androidx.compose.foundation.layout.height;
import androidx.compose.foundation.layout.padding;
import androidx.compose.foundation.layout.size;
import androidx.compose.foundation.layout.width;
import androidx.compose.material3.ExperimentalMaterial3Api;
import androidx.compose.material3.FilledIconToggleButton;
import androidx.compose.material3.Icon;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.material3.Surface;
import androidx.compose.material3.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.runtime.LaunchedEffect;
import androidx.compose.runtime.getValue;
import androidx.compose.runtime.mutableStateOf;
import androidx.compose.runtime.saveable.rememberSaveable;
import androidx.compose.runtime.setValue;
import androidx.compose.runtime.withFrameMillis;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.vector.ImageVector;
import androidx.compose.ui.layout.Layout;
import androidx.compose.ui.res.vectorResource;
import androidx.compose.ui.unit.dp;
import kotlin.math.log10;
import kotlin.math.max;
import kotlin.math.pow;
import vn.name.leduyquang753.finerecorder.ui.theme.FineRecorderTheme;

class MainActivity: ComponentActivity() {
	private var recorder by mutableStateOf<Recorder?>(null);
	private val recorderConnection: ServiceConnection = object: ServiceConnection {
		override fun onServiceConnected(className: ComponentName, service: IBinder) {
			val localRecorder = (service as Recorder.RecordingBinder).recorder;
			recorder = localRecorder;
			localRecorder.stopCallback = {
				recorder = null;
				unbindService(this);
			};
		}
		override fun onServiceDisconnected(className: ComponentName) {
			recorder = null;
		}
	};
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState);

		val notificationChannel = NotificationChannel(
			"vn.name.leduyquang753.finerecorder.notifications", "Fine recorder",
			NotificationManager.IMPORTANCE_LOW
		);
		notificationChannel.description = "The notifications from the app Fine recorder.";
		notificationChannel.setSound(null, null);
		(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
		.createNotificationChannel(notificationChannel);

		registerForActivityResult(RequestMultiplePermissions()) {}.launch(
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
				arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
			else
				arrayOf(Manifest.permission.RECORD_AUDIO)
		);
			
		val recordingIntent = Intent(this, Recorder::class.java);
		bindService(recordingIntent, recorderConnection, 0);
		
		setContent { FineRecorderTheme { Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			MainScreen(recorder) {
				val localRecorder = recorder;
				if (localRecorder == null) {
					startForegroundService(recordingIntent);
					bindService(recordingIntent, recorderConnection, 0);
				} else {
					localRecorder.stop();
				}
			};
		}; }; };
	}

	override fun onDestroy() {
		super.onDestroy();
		val localRecorder = recorder;
		if (localRecorder != null) {
			localRecorder.stopCallback = null;
			unbindService(recorderConnection);
			recorder = null;
		}
	}
}

@Composable
fun MainScreen(recorder: Recorder?, toggleRecording: () -> Unit) {
	var recordingTime by rememberSaveable { mutableStateOf(0L); };
	var lastLevel by rememberSaveable { mutableStateOf(0.0f); };
	var peakLevel by rememberSaveable { mutableStateOf(0.0f); };
	var peakLevelStaying by rememberSaveable { mutableStateOf(false); };
	var peakValue by rememberSaveable { mutableStateOf(0.0f); };
	
	Column(Modifier.padding(16.dp)) {
		Box(Modifier.fillMaxWidth()) { FilledIconToggleButton(
			recorder != null,
			{
				if (recorder == null) {
					recordingTime = 0L;
					lastLevel = 0.0f;
					peakLevel = 0.0f;
					peakLevelStaying = false;
				}
				toggleRecording();
			},
			Modifier.size(120.dp).align(Alignment.Center)
		) { Icon(
			ImageVector.Companion.vectorResource(
				id = if (recorder == null) R.drawable.baseline_mic_24 else R.drawable.baseline_stop_24
			),
			if (recorder == null) "Record" else "Stop",
			Modifier.size(90.dp)
		); }; };
		if (recorder != null) {
			val hours = recordingTime / 3600;
			val minutes = recordingTime % 3600 / 60;
			val seconds = recordingTime % 60;
			var timeText = "";
			if (hours != 0L) {
				timeText += hours;
				timeText += "h";
				if (minutes < 10) timeText += "0";
			}
			if (recordingTime >= 60) {
				timeText += minutes;
				timeText += ":";
				if (seconds < 10) timeText += "0";
			}
			timeText += seconds;
			if (recordingTime < 60) timeText += "\"";
			
			Text(recorder.fileName);
			Text(timeText, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.displaySmall);

			fun computeTextPosition(containerWidth: Int, elementWidth: Int, position: Float) = (
				if ((position - elementWidth / 2.0f) < 0) 0
				else if (position + elementWidth / 2.0f > containerWidth) containerWidth - elementWidth
				else position - elementWidth / 2.0f
			).toInt();
			
			Column(Modifier.weight(1.0f)) {
				Column(Modifier.height(24.dp).padding(bottom = 4.dp)) {
					Spacer(Modifier.weight(1.0f));
					if (peakLevel > 0.1f && peakLevelStaying) Layout({
						val cb /* Centibels. */ = (-200 * log10(peakValue)).toInt();
						Text("-${cb / 10},${cb % 10} dB", color = MaterialTheme.colorScheme.secondary);
					}) { rawElements, constraints ->
						val element = rawElements[0].measure(constraints);
						val width = constraints.maxWidth;
						val height = element.height;
						val position = peakLevel * width;
						layout(width, height) {
							element.placeRelative(computeTextPosition(width, element.width, position), 0);
						};
					};
				};
				Box {
					if (peakLevel > 0.1f) Row(
						Modifier
						.fillMaxWidth(peakLevel).height(16.dp)
					) {
						Spacer(Modifier.weight(1.0f));
						Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondary));
					};
					Box(
						Modifier
						.fillMaxWidth(lastLevel).height(16.dp)
						.background(MaterialTheme.colorScheme.primary)
					);
				};
				Layout({
					Text("0"); Text("-3"); Text("-6"); Text("-9");
					Text("-12"); Text("-18"); Text("-24");
					Text("-30"); Text("-42"); Text("-54"); Text("-78");
				}) { rawElements, constraints ->
					val elements = rawElements.map { rawElement -> rawElement.measure(constraints) };
					val width = constraints.maxWidth;
					val height = elements[0].height;
					layout(width, height) {
						elements.zip(listOf(
							0, -3, -6, -9,
							-12, -18, -24,
							-30, -42, -54, -78
						)) { element, db ->
							element.placeRelative(
								computeTextPosition(width, element.width, 10.0f.pow(db / 60.0f) * width), 0
							);
						};
					};
				};
			};
			
			LaunchedEffect(recorder) {
				var lastTime = withFrameMillis { time -> time };
				var lastPeakTime = -2000L;
				while (true) withFrameMillis { time ->
					val currentTime = SystemClock.elapsedRealtime();
					recordingTime =
						if (currentTime < recorder.startTime) 0L
						else (currentTime - recorder.startTime) / 1000;
					val value = recorder.level / Recorder.MAX_LEVEL.toFloat();
					lastLevel = max(lastLevel - (time - lastTime) / 1000.0f, value.pow(1/3.0f));
					if (time - lastPeakTime >= 2000) peakLevel = max(0.0f, peakLevel - (time - lastTime) / 1000.0f);
					if (lastLevel > peakLevel) {
						peakLevel = lastLevel;
						peakValue = value;
						lastPeakTime = time;
					}
					peakLevelStaying = time - lastPeakTime < 2000;
					lastTime = time;
				}
			};
		}
	}
}