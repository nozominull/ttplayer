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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import android.os.Environment;
import android.util.Log;

/**
 * Retrieves and organizes media to play. Before being used, you must call
 * {@link #prepare()}, which will retrieve all of the music on the user's device
 * (by performing a query on a content resolver). After that, it's ready to
 * retrieve a random song, with its title and URI, upon request.
 */
public class MusicRetriever {
	final String TAG = "MusicRetriever";

	private ArrayList<Song> songArray = new ArrayList<Song>();

	private Random mRandom = new Random();

	public MusicRetriever() {
	}

	/**
	 * Loads music data. This method may take long, so be sure to call it
	 * asynchronously without blocking the main thread.
	 */
	public void loadFromFile() {
		songArray.clear();

		try {
			File file = new File(Environment.getExternalStorageDirectory()
					+ "/ttplayer/playlist.txt");
			if (!file.exists()) {
				return;
			}
			FileInputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[1024];
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int length = 0;
			while ((length = fis.read(buffer, 0, buffer.length)) != -1) {
				if (length > 0) {
					bos.write(buffer, 0, length);
				}
			}
			fis.close();
			String result = new String(bos.toByteArray(), "UTF-8");
			bos.close();
			String[] resultSplit = result.split("\n");
			for (String path : resultSplit) {
				if (!path.equals("")) {
					songArray.add(new Song(path));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.e("loadFromFile", "total " + songArray.size());
	}

	public void loadFromFolder(String folderPath) {
		songArray.clear();
		File folder = new File(folderPath);
		File[] files = folder.listFiles();
		for (File file : files) {
			if (file.getName().endsWith(".mp3")) {
				songArray.add(new Song(file.getPath()));
			}
		}
		Collections.sort(songArray);

		try {
			folder = new File(Environment.getExternalStorageDirectory()
					+ "/ttplayer");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			FileOutputStream fos = new FileOutputStream(folder
					+ "/playlist.txt");
			for (Song song : songArray) {
				fos.write((song.getPath() + "\n").getBytes("UTF-8"));
			}
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.e("loadFromFolder", "total " + songArray.size());
	}

	/** Returns a random Item. If there are no items available, returns null. */
	public Song getRandomSong() {
		if (songArray.isEmpty()) {
			return null;
		}
		return songArray.get(mRandom.nextInt(songArray.size()));
	}

	public Song getFirstSong() {
		if (songArray.isEmpty()) {
			return null;
		}
		return songArray.get(0);
	}

	public Song getNextSong(Song song) {
		if (songArray.isEmpty()) {
			return null;
		}
		int index = songArray.indexOf(song);
		if (index == -1 || index == songArray.size() - 1) {
			return songArray.get(0);
		} else {
			return songArray.get(index + 1);
		}
	}

	public ArrayList<Song> getSongArray() {
		return songArray;
	}

	public void delete(Song song) {
		songArray.remove(song);
	}

}
