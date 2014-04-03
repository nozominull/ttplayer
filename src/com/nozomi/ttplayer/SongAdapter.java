package com.nozomi.ttplayer;

import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class SongAdapter extends BaseAdapter {
	private Context context = null;
	private ArrayList<Song> songArray = null;
	private int songOdd = 0;
	private int songEven = 0;

	public SongAdapter(Context context, ArrayList<Song> songArray) {
		this.context = context;
		this.songArray = songArray;
		songOdd = context.getResources().getColor(R.color.playlist_odd);
		songEven = context.getResources().getColor(R.color.playlist_even);
	}

	@Override
	public int getCount() {
		return songArray.size();
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = LayoutInflater.from(context).inflate(R.layout.song,
					null, false);
			holder.nameView = (TextView) convertView.findViewById(R.id.name);
			holder.deleteView = (ImageView) convertView
					.findViewById(R.id.delete);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		final Song song = songArray.get(position);
		int id = position + 1;
		if ((id & 1) == 1) {
			convertView.setBackgroundColor(songOdd);
		} else {
			convertView.setBackgroundColor(songEven);
		}
		if (id < 10) {
			holder.nameView.setText("    " + id + "." + song.getName());
		} else if (id < 100) {
			holder.nameView.setText("  " + id + "." +  song.getName());
		} else {
			holder.nameView.setText(id + "." +  song.getName());
		}
		
		holder.deleteView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				dialog(song);
			}
		});
		return convertView;
	}

	private class ViewHolder {
		TextView nameView;
		ImageView deleteView;
	}

	private void dialog(final Song song) {
		AlertDialog.Builder builder = new Builder(context);
		builder.setIcon(R.drawable.ic_launcher); // 设置图标		
		builder.setTitle("delete \"" + song.getName() + "\""); // 设置标题
		builder.setPositiveButton("delete file",
				new android.content.DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(MusicService.ACTION_DELETE);
						intent.putExtra("song", song);
						context.startService(intent);
						
						File file = new File(song.getPath());
						if (file.exists()) {
							file.delete();
						}						
					}
				});
		builder.setNeutralButton("ok",
				new android.content.DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {						
						Intent intent = new Intent(MusicService.ACTION_DELETE);
						intent.putExtra("song", song);
						context.startService(intent);
					}
				});
		builder.setNegativeButton("cancel",null);
		builder.create().show();
	}
}