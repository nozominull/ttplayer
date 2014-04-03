package com.nozomi.ttplayer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class PreferencesUtils {

	public enum Mode {
		Random, Order, Loop
	}

	private static final String SP_MODE = "mode";
	private static final String SP_VOLUME = "volume";
	private static final String SP_SONG = "song";

	private static Mode mode = null;
	private static int volume = -1;

	public static Mode getMode(Context context) {
		if (mode == null) {
			SharedPreferences sp = context.getSharedPreferences("ttplayer",
					Context.MODE_PRIVATE);
			mode = Mode.values()[sp.getInt(SP_MODE, Mode.Random.ordinal())];
		}
		return mode;
	}

	public static void setMode(Context context, Mode mode) {
		PreferencesUtils.mode = mode;

		SharedPreferences sp = context.getSharedPreferences("ttplayer",
				Context.MODE_PRIVATE);
		Editor editor = sp.edit();
		editor.putInt(SP_MODE, mode.ordinal());
		editor.commit();

	}

	public static int getVolume(Context context) {
		if (volume == -1) {
			SharedPreferences sp = context.getSharedPreferences("ttplayer",
					Context.MODE_PRIVATE);
			volume = sp.getInt(SP_VOLUME, 100);
		}
		return volume;
	}

	public static void setVolume(Context context, int volume, boolean isSave) {
		PreferencesUtils.volume = volume;
		Intent intent = new Intent(MusicService.ACTION_SET_VOLUME);
		context.startService(intent);

		if (isSave) {
			SharedPreferences sp = context.getSharedPreferences("ttplayer",
					Context.MODE_PRIVATE);
			Editor editor = sp.edit();
			editor.putInt(SP_VOLUME, volume);
			editor.commit();
		}
	}

	public static void saveSong(Context context, Song song) {
		SharedPreferences sp = context.getSharedPreferences("ttplayer",
				Context.MODE_PRIVATE);
		Editor editor = sp.edit();
		if(song==null){
			editor.putString(SP_SONG, "");
		}else{
		editor.putString(SP_SONG, song.getPath());
		}
		editor.commit();

	}

	public static Song loadSong(Context context) {
		SharedPreferences sp = context.getSharedPreferences("ttplayer",
				Context.MODE_PRIVATE);
		String path = sp.getString(SP_SONG, "");
		if (!path.equals("")) {
			return new Song(path);
		} else {
			return null;
		}

	}

}
