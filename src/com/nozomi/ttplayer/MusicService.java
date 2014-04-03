/*   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nozomi.ttplayer;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Service that handles media playback. This is the Service through which we
 * perform all the media handling in our application. Upon initialization, it
 * starts a {@link MusicRetriever} to scan the user's media. Then, it waits for
 * Intents (which come from our main activity, {@link MainActivity}, which
 * signal the service to perform specific operations: Play, Pause, Rewind, Skip,
 * etc.
 */
public class MusicService extends Service implements OnCompletionListener,
		OnPreparedListener, OnErrorListener, MusicFocusable,
		PrepareMusicRetrieverTask.MusicRetrieverPreparedListener {

	// The tag we put on debug messages
	final static String TAG = "ttplayer";

	// These are the Intent actions that we are prepared to handle. Notice that
	// the fact these
	// constants exist in our class is a mere convenience: what really defines
	// the actions our
	// service can handle are the <action> tags in the <intent-filters> tag for
	// our service in
	// AndroidManifest.xml.

	public static final String ACTION_PLAY = "com.nozomi.ttplayer.action.PLAY";
	public static final String ACTION_REWIND = "com.nozomi.ttplayer.action.REWIND";
	public static final String ACTION_PAUSE = "com.nozomi.ttplayer.action.PAUSE";
	public static final String ACTION_STOP = "com.nozomi.ttplayer.action.STOP";
	public static final String ACTION_SKIP = "com.nozomi.ttplayer.action.SKIP";
	public static final String ACTION_SET_FOLDER = "com.nozomi.ttplayer.action.SET_FOLDER";
	public static final String ACTION_DELETE = "com.nozomi.ttplayer.action.DELETE";
	public static final String ACTION_INIT = "com.nozomi.ttplayer.action.INIT";
	public static final String ACTION_PLAY_SELECT = "com.nozomi.ttplayer.action.PLAY_SELECT";
	public static final String ACTION_SET_VOLUME = "com.nozomi.ttplayer.action.SET_VOLUME";
	public static final String ACTION_GET_PROGRESS = "com.nozomi.ttplayer.action.GET_PROGRESS";
	public static final String ACTION_SET_PROGRESS = "com.nozomi.ttplayer.action.SET_PROGRESS";
	// The volume we set the media player to when we lose audio focus, but are
	// allowed to reduce
	// the volume instead of stopping playback.
	private static final float DUCK_VOLUME = 0.1f;

	// our media player
	private MediaPlayer mPlayer = null;

	// our AudioFocusHelper object, if it's available (it's available on SDK
	// level >= 8)
	// If not available, this will be null. Always check for null before using!
	private AudioFocusHelper mAudioFocusHelper = null;

	// indicates the state our service:
	private enum State {
		Retrieving, // the MediaRetriever is retrieving music
		Stopped, // media player is stopped and not prepared to play
		Preparing, // media player is preparing...
		Playing, // playback active (media player ready!). (but the media player
					// may actually be
					// paused in this state if we don't have audio focus. But we
					// stay in this state
					// so that we know we have to resume playback once we get
					// focus back)
		Paused // playback paused (media player ready!)
	};

	private State mState = State.Retrieving;

	// if in Retrieving mode, this flag indicates whether we should start
	// playing immediately
	// when we are ready or not.
	private boolean mStartPlayingAfterRetrieve = false;

	// if mStartPlayingAfterRetrieve is true, this variable indicates the URL
	// that we should
	// start playing when we are ready. If null, we should play a random song
	// from the device
	private Song mWhatToPlayAfterRetrieve = null;

	enum PauseReason {
		UserRequest, // paused by user request
		FocusLoss, // paused because of audio focus loss
	};

	// do we have audio focus?
	enum AudioFocus {
		NoFocusNoDuck, // we don't have audio focus, and can't duck
		NoFocusCanDuck, // we don't have focus, but can play at a low volume
						// ("ducking")
		Focused // we have full audio focus
	}

	private AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

	// title of the song we are currently playing

	private Song song = null;

	// The ID we use for the notification (the onscreen alert that appears at
	// the notification
	// area at the top of the screen as an icon -- and as text as well if the
	// user expands the
	// notification area).
	private final int NOTIFICATION_ID = 1;

	// Our instance of our MusicRetriever, which handles scanning for media and
	// providing titles and URIs as we need.
	private MusicRetriever mRetriever;
	private NotificationManager mNotificationManager;

	private Notification mNotification = null;

	/**
	 * Makes sure the media player exists and has been reset. This will create
	 * the media player if needed, or reset the existing media player if one
	 * already exists.
	 */
	private void createMediaPlayerIfNeeded() {
		if (mPlayer == null) {
			mPlayer = new MediaPlayer();

			// Make sure the media player will acquire a wake-lock while
			// playing. If we don't do
			// that, the CPU might go to sleep while the song is playing,
			// causing playback to stop.
			//
			// Remember that to use this, we have to declare the
			// android.permission.WAKE_LOCK
			// permission in AndroidManifest.xml.
			mPlayer.setWakeMode(getApplicationContext(),
					PowerManager.PARTIAL_WAKE_LOCK);

			// we want the media player to notify us when it's ready preparing,
			// and when it's done
			// playing:
			mPlayer.setOnPreparedListener(this);
			mPlayer.setOnCompletionListener(this);
			mPlayer.setOnErrorListener(this);
		} else {
			mPlayer.reset();
		}
	}

	@Override
	public void onCreate() {
		Log.e("onCreate", "onCreate");

		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		// Create the retriever and start an asynchronous task that will prepare
		// it.
		mRetriever = new MusicRetriever();

		// create the Audio Focus Helper, if the Audio Focus feature is
		// available (SDK 8 or above)
		mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(), this);

		mWhatToPlayAfterRetrieve = PreferencesUtils.loadSong(this);
		if (mWhatToPlayAfterRetrieve != null) {
			mStartPlayingAfterRetrieve = true;
		}

		(new PrepareMusicRetrieverTask(mRetriever, this)).execute();
	}

	/**
	 * Called when we receive an Intent. When we receive an intent sent to us
	 * via startService(), this is the method that gets called. So here we react
	 * appropriately depending on the Intent's action, which specifies what is
	 * being requested of us.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if (action.equals(ACTION_PLAY)) {
			processPlayRequest();
		} else if (action.equals(ACTION_PAUSE)) {
			processPauseRequest();
		} else if (action.equals(ACTION_SKIP)) {
			processSkipRequest();
		} else if (action.equals(ACTION_STOP)) {
			processStopRequest();
		} else if (action.equals(ACTION_REWIND)) {
			processRewindRequest();
		} else if (action.equals(ACTION_SET_FOLDER)) {
			processSetFolderRequest(intent);
		} else if (action.equals(ACTION_DELETE)) {
			processDeleteRequest(intent);
		} else if (action.equals(ACTION_INIT)) {
			processInitRequest();
		} else if (action.equals(ACTION_PLAY_SELECT)) {
			processPlaySelectRequest(intent);
		} else if (action.equals(ACTION_SET_VOLUME)) {
			processPlaySetVolumeRequest();
		} else if (action.equals(ACTION_GET_PROGRESS)) {
			processGetProgressRequest();
		} else if (action.equals(ACTION_SET_PROGRESS)) {
			processSetProgressRequest(intent);
		}

		return START_NOT_STICKY; // Means we started the service, but don't want
									// it to
									// restart in case it's killed.
	}

	private void processPlayRequest() {
		if (mState == State.Retrieving) {
			// If we are still retrieving media, just set the flag to start
			// playing when we're
			// ready
			mStartPlayingAfterRetrieve = true;
			return;
		}

		tryToGetAudioFocus();

		// actually play the song

		if (mState == State.Stopped) {
			// If we're stopped, just go ahead to the next song and start
			// playing
			playNextSong(null);
		} else if (mState == State.Paused) {
			// If we're paused, just continue playback and restore the
			// 'foreground service' state.
			mState = State.Playing;
			processUpdateStateRequest();
			setUpAsForeground(song.getName() + " (playing)");
			configAndStartMediaPlayer();

		}
	}

	private void processPauseRequest() {
		if (mState == State.Retrieving) {
			// If we are still retrieving media, clear the flag that indicates
			// we should start
			// playing when we're ready
			mStartPlayingAfterRetrieve = false;
			return;
		}

		if (mState == State.Playing) {
			// Pause media player and cancel the 'foreground service' state.
			mState = State.Paused;
			processUpdateStateRequest();
			mPlayer.pause();
			relaxResources(false); // while paused, we always retain the
									// MediaPlayer

		}
	}

	private void processRewindRequest() {
		if (mState == State.Playing || mState == State.Paused) {
			mPlayer.seekTo(0);
			processGetProgressRequest();
		}
	}

	private void processSetFolderRequest(Intent intent) {
		processStopRequest();
		String folderPath = intent.getStringExtra("folder_path");
		mState = State.Retrieving;
		processUpdateStateRequest();
		mStartPlayingAfterRetrieve = false;

		(new PrepareMusicRetrieverTask(mRetriever, this, folderPath)).execute();

	}

	private void processDeleteRequest(Intent intent) {
		Song song = (Song) intent.getSerializableExtra("song");
		mRetriever.delete(song);

		intent = new Intent(MainActivity.ACTION_UPDATE_SONG_LIST);
		intent.putExtra("song_array", mRetriever.getSongArray());
		sendBroadcast(intent);

		if (song.equals(this.song)) {
			processStopRequest();
		}
	}

	private void processInitRequest() {
		processUpdateStateRequest();

		if (mState != State.Retrieving) {
			Intent intent = new Intent(MainActivity.ACTION_UPDATE_SONG_LIST);
			intent.putExtra("song_array", mRetriever.getSongArray());
			sendBroadcast(intent);
		}

		if (mState == State.Playing || mState == State.Paused) {
			Intent intent = new Intent(MainActivity.ACTION_UPDATE_PLAYER);
			intent.putExtra("song", song);
			intent.putExtra("duration", mPlayer.getDuration());
			sendBroadcast(intent);
		}

	}

	private void processPlaySelectRequest(Intent intent) {

		// user wants to play a song directly by URL or path. The URL or path
		// comes in the "data"
		// part of the Intent. This Intent is sent by {@link MainActivity} after
		// the user
		// specifies the URL/path via an alert box.
		if (mState == State.Retrieving) {
			// we'll play the requested URL right after we finish retrieving
			mWhatToPlayAfterRetrieve = (Song) intent
					.getSerializableExtra("song");
			mStartPlayingAfterRetrieve = true;
		} else if (mState == State.Playing || mState == State.Paused
				|| mState == State.Stopped) {
			tryToGetAudioFocus();
			playNextSong((Song) intent.getSerializableExtra("song"));
		}
	}

	private void processPlaySetVolumeRequest() {
		if (mPlayer != null) {
			float volume = PreferencesUtils.getVolume(this) * 0.01f;
			mPlayer.setVolume(volume, volume);
		}
	}

	private void processSkipRequest() {
		if (mState == State.Playing || mState == State.Paused) {
			tryToGetAudioFocus();
			playNextSong(null);
		}
	}

	private void processStopRequest() {
		processStopRequest(false);
	}

	private void processStopRequest(boolean force) {
		if (mState == State.Playing || mState == State.Paused || force) {
			if (mState == State.Playing && song != null) {
				PreferencesUtils.saveSong(this, song);
			}else{
				PreferencesUtils.saveSong(this, null);
			}

			mState = State.Stopped;
			processUpdateStateRequest();

			// let go of all resources...
			relaxResources(true);
			giveUpAudioFocus();

			// service is no longer necessary. Will be started again if needed.
			stopSelf();
		}
	}

	private void processGetProgressRequest() {
		Intent intent = new Intent(MainActivity.ACTION_UPDATE_PROGRESS);
		if (mState == State.Playing || mState == State.Paused) {
			intent.putExtra("current_position", mPlayer.getCurrentPosition());
			intent.putExtra("duration", mPlayer.getDuration());
			intent.putExtra("is_playing", true);
		} else {
			intent.putExtra("current_position", 0);
			intent.putExtra("duration", 0);
			intent.putExtra("is_playing", false);
		}
		sendBroadcast(intent);
	}

	private void processSetProgressRequest(Intent intent) {
		if (mState == State.Playing || mState == State.Paused) {
			int progress = intent.getIntExtra("progress", 0);
			mPlayer.seekTo(progress * mPlayer.getDuration() / 1000);
			processGetProgressRequest();
		}
	}

	private void processUpdateStateRequest() {
		Log.e("processUpdateStateRequest", mState.name());
		Intent intent = new Intent(MainActivity.ACTION_UPDATE_STATE);
		if (mState == State.Playing) {
			intent.putExtra("is_playing", true);
		} else {
			intent.putExtra("is_playing", false);
		}
		sendBroadcast(intent);
	}

	/**
	 * Releases resources used by the service for playback. This includes the
	 * "foreground service" status and notification, the wake locks and possibly
	 * the MediaPlayer.
	 * 
	 * @param releaseMediaPlayer
	 *            Indicates whether the Media Player should also be released or
	 *            not
	 */
	private void relaxResources(boolean releaseMediaPlayer) {
		// stop being a foreground service
		stopForeground(true);

		// stop and release the Media Player, if it's available
		if (releaseMediaPlayer && mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
			mPlayer = null;
		}

	}

	private void giveUpAudioFocus() {
		if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
				&& mAudioFocusHelper.abandonFocus())
			mAudioFocus = AudioFocus.NoFocusNoDuck;
	}

	/**
	 * Reconfigures MediaPlayer according to audio focus settings and
	 * starts/restarts it. This method starts/restarts the MediaPlayer
	 * respecting the current audio focus state. So if we have focus, it will
	 * play normally; if we don't have focus, it will either leave the
	 * MediaPlayer paused or set it to a low volume, depending on what is
	 * allowed by the current focus settings. This method assumes mPlayer !=
	 * null, so if you are calling it, you have to do so from a context where
	 * you are sure this is the case.
	 */
	private void configAndStartMediaPlayer() {
		if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
			// If we don't have audio focus and can't duck, we have to pause,
			// even if mState
			// is State.Playing. But we stay in the Playing state so that we
			// know we have to resume
			// playback once we get the focus back.
			if (mPlayer.isPlaying()) {
				mPlayer.pause();
			}
			return;
		} else if (mAudioFocus == AudioFocus.NoFocusCanDuck) {
			mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME); // we'll be relatively
															// quiet
		} else {
			float volume = PreferencesUtils.getVolume(this) * 0.01f;
			mPlayer.setVolume(volume, volume);
		}

		if (!mPlayer.isPlaying()) {
			mPlayer.start();
		}
	}

	private void tryToGetAudioFocus() {
		if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
				&& mAudioFocusHelper.requestFocus())
			mAudioFocus = AudioFocus.Focused;
	}

	/**
	 * Starts playing the next song. If manualUrl is null, the next song will be
	 * randomly selected from our Media Retriever (that is, it will be a random
	 * song in the user's device). If manualUrl is non-null, then it specifies
	 * the URL or path to the song that will be played next.
	 */
	private void playNextSong(Song song) {
		mState = State.Stopped;
		processUpdateStateRequest();
		relaxResources(false); // release everything except MediaPlayer

		try {
			if (song == null) {
				PreferencesUtils.Mode mode = PreferencesUtils.getMode(this);
				if (mode == PreferencesUtils.Mode.Random) {
					song = mRetriever.getRandomSong();
				} else if (mode == PreferencesUtils.Mode.Order) {
					if (this.song == null) {
						song = mRetriever.getFirstSong();
					} else {
						song = mRetriever.getNextSong(this.song);
					}
				} else if (mode == PreferencesUtils.Mode.Loop) {
					if (this.song == null) {
						song = mRetriever.getFirstSong();
					} else {
						song = this.song;
					}
				}
				if (song == null) {
					Toast.makeText(
							this,
							"No available music to play. Place some music on your external storage "
									+ "device (e.g. your SD card) and try again.",
							Toast.LENGTH_LONG).show();
					processStopRequest(true); // stop everything!
					return;
				}
			}
			this.song = song;
			// set the source of the media player a a content URI
			createMediaPlayerIfNeeded();
			mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mPlayer.setDataSource(song.getPath());

			mState = State.Preparing;
			setUpAsForeground(song.getName() + " (loading)");

			// starts preparing the media player in the background. When it's
			// done,
			// it will call
			// our OnPreparedListener (that is, the onPrepared() method on this
			// class, since we set
			// the listener to 'this').
			//
			// Until the media player is prepared, we *cannot* call start() on
			// it!
			mPlayer.prepareAsync();
		} catch (IOException ex) {
			Log.e("MusicService",
					"IOException playing next song: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	/** Called when media player is done playing current song. */
	@Override
	public void onCompletion(MediaPlayer player) {
		// The media player finished playing the current song, so we go ahead
		// and start the next.
		playNextSong(null);
	}

	/** Called when media player is done preparing. */
	@Override
	public void onPrepared(MediaPlayer player) {
		// The media player is done preparing. That means we can start playing!
		mState = State.Playing;
		processUpdateStateRequest();

		updateNotification(song.getName() + " (playing)");
		configAndStartMediaPlayer();

		Intent intent = new Intent(MainActivity.ACTION_UPDATE_PLAYER);
		intent.putExtra("song", song);
		intent.putExtra("duration", mPlayer.getDuration());
		sendBroadcast(intent);
	}

	/** Updates the notification. */
	private void updateNotification(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
				0, new Intent(getApplicationContext(), MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		mNotification.setLatestEventInfo(getApplicationContext(), "ttplayer",
				text, pi);
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Configures service as a foreground service. A foreground service is a
	 * service that's doing something the user is actively aware of (such as
	 * playing music), and must appear to the user as a notification. That's why
	 * we create the notification here.
	 */
	private void setUpAsForeground(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
				0, new Intent(getApplicationContext(), MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		mNotification = new Notification();
		mNotification.tickerText = text;
		mNotification.icon = R.drawable.ic_launcher;
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		mNotification.setLatestEventInfo(getApplicationContext(), "ttplayer",
				text, pi);
		startForeground(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Called when there's an error playing media. When this happens, the media
	 * player goes to the Error state. We warn the user about the error and
	 * reset the media player.
	 */
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Toast.makeText(getApplicationContext(),
				"Media player error! Resetting.", Toast.LENGTH_SHORT).show();
		Log.e(TAG,
				"Error: what=" + String.valueOf(what) + ", extra="
						+ String.valueOf(extra));

		mState = State.Stopped;
		processUpdateStateRequest();

		relaxResources(true);
		giveUpAudioFocus();

		return true; // true indicates we handled the error
	}

	@Override
	public void onGainedAudioFocus() {
		Toast.makeText(getApplicationContext(), "gained audio focus.",
				Toast.LENGTH_SHORT).show();
		mAudioFocus = AudioFocus.Focused;

		// restart media player with new focus settings
		if (mState == State.Playing) {
			configAndStartMediaPlayer();
		}
	}

	@Override
	public void onLostAudioFocus(boolean canDuck) {
		Toast.makeText(getApplicationContext(),
				"lost audio focus." + (canDuck ? "can duck" : "no duck"),
				Toast.LENGTH_SHORT).show();
		mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck
				: AudioFocus.NoFocusNoDuck;

		// start/restart/pause media player with new focus settings
		if (mPlayer != null && mPlayer.isPlaying()) {
			configAndStartMediaPlayer();
		}
	}

	@Override
	public void onMusicRetrieverPrepared() {
		// Done retrieving!
		mState = State.Stopped;
		processUpdateStateRequest();

		Intent intent = new Intent(MainActivity.ACTION_UPDATE_SONG_LIST);
		intent.putExtra("song_array", mRetriever.getSongArray());
		sendBroadcast(intent);

		// If the flag indicates we should start playing after retrieving, let's
		// do that now.
		if (mStartPlayingAfterRetrieve) {
			mStartPlayingAfterRetrieve = false;
			tryToGetAudioFocus();
			playNextSong(mWhatToPlayAfterRetrieve);
		}
	}

	@Override
	public void onDestroy() {
		// Service is being killed, so make sure we release our resources
		mState = State.Stopped;
		processUpdateStateRequest();

		relaxResources(true);
		giveUpAudioFocus();

	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
}
