package com.joshlong.youtube.explorer;

import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.Playlist;
import com.joshlong.youtube.client.Video;
import com.joshlong.youtube.client.YoutubeClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;

@Controller
class YoutubeGraphqlController {

	private final YoutubeClient yt;

	YoutubeGraphqlController(YoutubeClient youtubeClient) {
		this.yt = youtubeClient;
	}

	@QueryMapping
	Mono<Channel> channelByUsername(@Argument String username) {
		return this.yt.getChannelByUsername(username);
	}

	@SchemaMapping(typeName = "Channel")
	Flux<Playlist> playlists(Channel channel) {
		return this.yt.getAllPlaylistsByChannel(channel.channelId());
	}

	@SchemaMapping(typeName = "Playlist")
	Mono<Playlist> playlistById(Playlist playlist, @Argument String id) {
		return null;
	}

	@SchemaMapping(typeName = "Playlist")
	Flux<Video> videos(Playlist playlist) {
		return this.yt.getAllVideosByPlaylist(playlist.playlistId());
	}

	@SchemaMapping(typeName = "Video")
	String standardThumbnail(Video video) {
		return video.standardThumbnail().toExternalForm();
	}

	@SchemaMapping(typeName = "Playlist")
	String publishedAt(Playlist playlist) {
		return date(playlist.publishedAt());
	}

	@SchemaMapping(typeName = "Channel")
	String publishedAt(Channel playlist) {
		return date(playlist.publishedAt());
	}

	private static String date(Date date) {
		return date.toInstant().toString();
	}

}
