package com.nozomi.ttplayer;

import java.util.ArrayList;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class MainActivity extends Activity {

	// TODO:save before close

	private static final int FOLDER_ACTIVITY = 1;
	public static final String ACTION_UPDATE_SONG_LIST = "com.nozomi.ttplayer.action.UPDATE_SONG_LIST";
	public static final String ACTION_UPDATE_PLAYER = "com.nozomi.ttplayer.action.UPDATE_PLAYER";
	public static final String ACTION_UPDATE_PROGRESS = "com.nozomi.ttplayer.action.UPDATE_PROGRESS";
	public static final String ACTION_UPDATE_STATE = "com.nozomi.ttplayer.action.UPDATE_STATE";

	private TextView nameView = null;
	private TextView positionDurationView = null;
	private SeekBar progressView = null;
	private ImageButton stateView = null;

	private UpdateViewReceiver updateViewReceiver = new UpdateViewReceiver();
	private IntentFilter filter = new IntentFilter();
	private SongAdapter songAdapter = null;
	private ArrayList<Song> songArray = new ArrayList<Song>();
	private ListView songListView = null;
	private boolean isPlaying = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_activity);

		filter.addAction(ACTION_UPDATE_SONG_LIST);
		filter.addAction(ACTION_UPDATE_PLAYER);
		filter.addAction(ACTION_UPDATE_PROGRESS);
		filter.addAction(ACTION_UPDATE_STATE);
		initView();

	}

	@Override
	protected void onResume() {
		super.onResume();

		registerReceiver(updateViewReceiver, filter);

		Intent intent = new Intent(MusicService.ACTION_INIT);
		startService(intent);
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(updateViewReceiver);
	}

	private void initView() {
		nameView = (TextView) findViewById(R.id.name);
		positionDurationView = (TextView) findViewById(R.id.position_duration);

		ImageButton rewindView = (ImageButton) findViewById(R.id.rewind);
		rewindView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MusicService.ACTION_REWIND);
				startService(intent);
			}
		});

		ImageButton closeView = (ImageButton) findViewById(R.id.close);
		closeView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				handler.removeMessages(1);
				Intent intent = new Intent(MusicService.ACTION_STOP);
				startService(intent);
				finish();
			}
		});

		stateView = (ImageButton) findViewById(R.id.state);
		stateView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (isPlaying) {
					Intent intent = new Intent(MusicService.ACTION_PAUSE);
					startService(intent);
				} else {
					Intent intent = new Intent(MusicService.ACTION_PLAY);
					startService(intent);
				}

			}
		});

		ImageButton skipView = (ImageButton) findViewById(R.id.skip);
		skipView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MusicService.ACTION_SKIP);
				startService(intent);
			}
		});

		final ImageButton modeView = (ImageButton) findViewById(R.id.mode);
		if (PreferencesUtils.getMode(this) == PreferencesUtils.Mode.Random) {
			modeView.setBackgroundResource(R.drawable.main_random);
		} else if (PreferencesUtils.getMode(this) == PreferencesUtils.Mode.Order) {
			modeView.setBackgroundResource(R.drawable.main_order);
		} else if (PreferencesUtils.getMode(this) == PreferencesUtils.Mode.Loop) {
			modeView.setBackgroundResource(R.drawable.main_loop);
		}
		modeView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (PreferencesUtils.getMode(MainActivity.this) == PreferencesUtils.Mode.Random) {
					PreferencesUtils.setMode(MainActivity.this,
							PreferencesUtils.Mode.Order);
					modeView.setBackgroundResource(R.drawable.main_order);
				} else if (PreferencesUtils.getMode(MainActivity.this) == PreferencesUtils.Mode.Order) {
					PreferencesUtils.setMode(MainActivity.this,
							PreferencesUtils.Mode.Loop);
					modeView.setBackgroundResource(R.drawable.main_loop);
				} else if (PreferencesUtils.getMode(MainActivity.this) == PreferencesUtils.Mode.Loop) {
					PreferencesUtils.setMode(MainActivity.this,
							PreferencesUtils.Mode.Random);
					modeView.setBackgroundResource(R.drawable.main_random);
				}
			}
		});

		Button setFolderView = (Button) findViewById(R.id.set_folder);
		setFolderView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this,
						FolderActivity.class);
				startActivityForResult(intent, FOLDER_ACTIVITY);
			}
		});

		songListView = (ListView) findViewById(R.id.song_list);
		songAdapter = new SongAdapter(this, songArray);
		songListView.setAdapter(songAdapter);
		songListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent intent = new Intent(MusicService.ACTION_PLAY_SELECT);
				intent.putExtra("song", songArray.get(position));
				startService(intent);
			}
		});

		progressView = (SeekBar) findViewById(R.id.progress);
		progressView.setTag(0);
		progressView.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					int duration = (Integer) progressView.getTag();
					int currentPosition = duration * progress / 1000;
					positionDurationView
							.setText(getTimeDisplay(currentPosition) + "/"
									+ getTimeDisplay(duration));
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Intent intent = new Intent(MusicService.ACTION_SET_PROGRESS);
				intent.putExtra("progress", seekBar.getProgress());
				startService(intent);

			}

		});

		SeekBar volumeView = (SeekBar) findViewById(R.id.volume);
		int volume = PreferencesUtils.getVolume(MainActivity.this);
		volumeView.setProgress(volume);
		volumeView.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				PreferencesUtils.setVolume(MainActivity.this,
						seekBar.getProgress(), true);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					PreferencesUtils.setVolume(MainActivity.this, progress,
							false);
				}
			}
		});

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == FOLDER_ACTIVITY) {
			if (resultCode == RESULT_OK) {
				String folderPath = data.getStringExtra("folder_path");
				Intent intent = new Intent(MusicService.ACTION_SET_FOLDER);
				intent.putExtra("folder_path", folderPath);
				startService(intent);
			}
		}
	}

	private class UpdateViewReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.e("onReceive", action);
			if (action.equals(ACTION_UPDATE_SONG_LIST)) {
				songArray.clear();
				songArray.addAll((ArrayList<Song>) intent
						.getSerializableExtra("song_array"));
				songAdapter.notifyDataSetChanged();
				songListView.setSelection(0);
				Log.e("ACTION_UPDATE_SONG_LIST", "total " + songArray.size());
			} else if (action.equals(ACTION_UPDATE_PROGRESS)) {
				if (!progressView.isPressed()) {
					int currentPosition = intent.getIntExtra(
							"current_position", 0);
					int duration = intent.getIntExtra("duration", 0);
					positionDurationView
							.setText(getTimeDisplay(currentPosition) + "/"
									+ getTimeDisplay(duration));
					if (duration == 0) {
						progressView.setProgress(0);
					} else {
						progressView.setProgress(currentPosition * 1000
								/ duration);
					}
				}

				isPlaying = intent.getBooleanExtra("is_playing", true);
				if (isPlaying) {
					handler.sendEmptyMessageDelayed(1, 500);
				}

			} else if (action.equals(ACTION_UPDATE_PLAYER)) {
				Song song = (Song) intent.getSerializableExtra("song");
				int index = songArray.indexOf(song);
				if (index != -1) {
					songListView.setSelection(index);
				}
				nameView.setText(song.getName());
				int duration = intent.getIntExtra("duration", 0);
				progressView.setTag(duration);

				handler.sendEmptyMessage(1);
			} else if (action.equals(ACTION_UPDATE_STATE)) {
				isPlaying = intent.getBooleanExtra("is_playing", false);
				Log.e("ACTION_UPDATE_STATE", String.valueOf(isPlaying));
				if (isPlaying) {
					stateView.setBackgroundResource(R.drawable.main_pause);
				} else {
					stateView.setBackgroundResource(R.drawable.main_play);
				}
			}
		}
	}

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int what = msg.what;
			if (what == 1) {// thread update progress
				Intent intent = new Intent(MusicService.ACTION_GET_PROGRESS);
				startService(intent);
				Log.e("handler", "ACTION_GET_PROGRESS");

			}
		}
	};

	private String getTimeDisplay(int time) {
		String output = "";
		int min = time / 1000 / 60;
		if (min < 10) {
			output += "0" + min;
		} else {
			output += min;
		}
		output += ":";
		int sec = time / 1000 % 60;
		if (sec < 10) {
			output += "0" + sec;
		} else {
			output += sec;
		}
		return output;
	}

}
