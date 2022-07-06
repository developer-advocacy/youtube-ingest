package com.joshlong.youtube.client;

import java.util.Date;

public record Playlist(String playlistId, String channelId, Date publishedAt, String title, String description,
		int itemCount) {
}
