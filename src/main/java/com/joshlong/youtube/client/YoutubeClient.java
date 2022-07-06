package com.joshlong.youtube.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * A simple YouTube client for the APIs
 * <a href="https://developers.google.com/youtube/v3/docs/channels/list">I need from the
 * YouTube API</a>.
 *
 * @author Josh Long
 */
public interface YoutubeClient {

	/**
	 * Return the playlists
	 * @param channelId the ID of the channel that we want to query
	 * @return all the {@link Playlist}s for a given {@link Channel}
	 */
	Flux<Playlist> getPlaylistsForChannel(String channelId);

	/**
	 * Finds a youtube channel by the username that created it.
	 * @param username a username, like {@code SpringDeveloper}
	 * @return a {@link Channel channel} that contains the metadata for a given Youtube
	 * channel
	 */
	Mono<Channel> getChannelByUsername(String username);

	/**
	 * Finds a youtube channel by the channel ID associated with the username that created
	 * it
	 * @param channelId a channel ID (each YouTube channel can be find by a username or an
	 * ID)
	 * @return a {@link Channel channel} that contains the metadata for a given Youtube
	 * channel
	 */
	Mono<Channel> getChannelById(String channelId);

	/**
	 * This returns all the videos associated with a collection of {@link String }
	 * videoIds
	 * @param videoIds takes a collection of {@link String} videoIds and then returns a
	 * {@link Map<String,Video>} results
	 * @return a map of videoIds to {@link Video}
	 */
	Mono<Map<String, Video>> getVideosByIds(List<String> videoIds);

	/**
	 * This in turn delegates to {@link #getVideosByIds(List)} but for a single
	 * {@link Video record}.
	 * @param videoId find a record by a single ID
	 * @return {@link Video} associated with the {@link String videoId}
	 */
	Mono<Video> getVideoById(String videoId);

	Mono<Playlist> getPlaylistById(String playlistId);

}
