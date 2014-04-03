package com.nozomi.ttplayer;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class FolderActivity extends Activity implements OnClickListener,
		OnItemClickListener {

	private String current = "";
	private ArrayList<String> fileArray = null;
	private ArrayAdapter<String> adapter = null;
	private TextView currentView = null;
	private ListView listView = null;
	private Button backView = null;
	private Button okView = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.folder_activity);

		current = Environment.getExternalStorageDirectory().getPath();
		currentView = (TextView) findViewById(R.id.current);
		currentView.setText("current:" + current);

		listView = (ListView) findViewById(R.id.list);
		listView.setOnItemClickListener(this);
		fileArray = new ArrayList<String>();
		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, fileArray);
		listView.setAdapter(adapter);

		backView = (Button) findViewById(R.id.back);
		backView.setOnClickListener(this);

		okView = (Button) findViewById(R.id.ok);
		okView.setOnClickListener(this);

		getFileArray(new File(current));
	}

	@Override
	public void onClick(View v) {
		if (v == backView) {
			current = current.substring(0, current.lastIndexOf("/"));
			if (current.equals("")) {
				current = "/";
			}
			currentView.setText("current:" + current);
			File file = new File(current);
			getFileArray(file);
			if (current.equals("/")) {
				backView.setVisibility(View.INVISIBLE);
			}
		} else if (v == okView) {
			Intent intent = new Intent();
			intent.putExtra("folder_path", current);
			setResult(RESULT_OK, intent);
			finish();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		File file = new File(current + "/" + fileArray.get(position));
		if (file.canRead()) {
			if (file.isDirectory()) {
				current = file.getPath();
				currentView.setText("current:" + current);
				getFileArray(file);
				backView.setVisibility(View.VISIBLE);
			} else {
				new AlertDialog.Builder(this)
						.setTitle("Message")
						.setMessage("\"" + file.getName() + "\" is a file!")
						.setPositiveButton("ok",null).show();
			}
		} else {
			new AlertDialog.Builder(this)
					.setTitle("Message")
					.setMessage("Access denied")
					.setPositiveButton("ok",null).show();
		}
	}

	private void getFileArray(File folder) {
		fileArray.clear();
		File[] files = folder.listFiles();
		for (File file : files) {
			fileArray.add(file.getName());
		}
		Collections.sort(fileArray,
				Collator.getInstance(Locale.CHINA));
		adapter.notifyDataSetChanged();
	}

}
