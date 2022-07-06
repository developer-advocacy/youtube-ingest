package com.joshlong.youtube.client;

import org.springframework.lang.Nullable;
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
	 * Returns all the videos for a given {@link Playlist}.
	 * @param playlistId the id of the {@link Playlist } in which to find {@link Video}s
	 * @param pageToken the token representing the next page in the series of
	 * {@link Video}s. This value can be null and is not required. If not specified, the
	 * method will return the first page of results.
	 * @return returns all the {@link Video}s for a given {@link Playlist}s.
	 */
	Mono<PlaylistVideos> getVideosByPlaylist(String playlistId, @Nullable String pageToken);

	/**
	 * This hides the underlying pagination model that the YouTube API imposes, letting
	 * you consume all the {@link Video}s as a single stream, rather than having to deal
	 * with {@link PlaylistVideos} and the implied pagination model surfaced in
	 * {@link this#getVideosByPlaylist(String, String)}.
	 * @param playlistId this returns all the {@link Video} items in a particular
	 * {@link Playlist}.
	 * @return all the videos within a playlist, hiding the underlying pagination from the
	 * client.
	 */
	Flux<Video> getAllVideosByPlaylist(String playlistId);

	/**
	 * Return the playlists
	 * @param channelId the ID of the channel that we want to query
	 * @param nextPageToken this represents the parameter to be fed into the next request
	 * to get the next page of results.
	 * @return all the {@link Playlist}s for a given {@link Channel}
	 */
	Mono<ChannelPlaylists> getPlaylistsByChannel(String channelId, String nextPageToken);

	/**
	 * Provides all the {@link Playlist playlists} for a given {@link Channel} hiding the
	 * details of the underlying pagination.
	 * @param channelId the id of the channel whose playlists we want
	 * @return a stream of {@link Playlist}s
	 */
	Flux<Playlist> getAllPlaylistsByChannel(String channelId);

	/**
	 * Finds a Youtube channel by the username that created it.
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
	 * This returns all the videos associated with a collection of {@link String}
	 * videoIds.
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

}
