package com.nozomi.ttplayer;

import java.io.Serializable;
import java.text.Collator;

public class Song implements Serializable, Comparable<Song> {

	private static final long serialVersionUID = 1L;
	private String path;
	private String name;
	private static Collator collator = Collator
			.getInstance(java.util.Locale.CHINA);

	public Song() {
		super();
	}

	public Song(String path) {
		super();
		this.path = path;
		int startIndex = path.lastIndexOf("/") + "/".length();
		int endIndex = path.lastIndexOf(".");
		this.name = path.substring(startIndex, endIndex);
	}

	public Song(String path, String name) {
		super();
		this.path = path;
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int compareTo(Song another) {
		if (name.equals(another.getName())) {
			return collator.compare(path, another.path);
		} else {
			return collator.compare(name, another.name);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Song other = (Song) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

}
