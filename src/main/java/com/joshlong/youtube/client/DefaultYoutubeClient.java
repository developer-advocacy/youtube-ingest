package com.joshlong.youtube.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Instant;
import java.util.*;

@Slf4j
class DefaultYoutubeClient implements YoutubeClient {

	private final WebClient http;

	private final String apiKey;

	@Override
	public Mono<Channel> getChannelByUsername(String username) {
		return findChannel(username, "&forUsername={username}", Map.of("username", username));
	}

	@Override
	public Mono<Channel> getChannelById(String channelId) {
		return findChannel(channelId, "&id={id}", Map.of("id", channelId));
	}

	@SneakyThrows
	private Video buildVideoFromJsonNode(JsonNode item) {
		var id = item.get("id").textValue();
		var publishedAt = buildDateFrom(item.get("snippet").get("publishedAt").textValue());
		var description = item.get("snippet").get("description").textValue();
		var title = item.get("snippet").get("title").textValue();
		var thumbnailUrl = new URL(item.get("snippet").get("thumbnails").get("default").get("url").textValue());
		var tags = item.get("snippet").get("tags");
		var statistics = item.get("statistics");
		var viewCount = Integer.parseInt(statistics.get("viewCount").textValue());
		var likeCount = Integer.parseInt(statistics.get("likeCount").textValue());
		var favCount = Integer.parseInt(statistics.get("favoriteCount").textValue());
		var commentCount = Integer.parseInt(statistics.get("commentCount").textValue());
		var categoryId = Integer.parseInt(item.get("snippet").get("categoryId").textValue());
		var tagsList = new ArrayList<String>();
		for (var tag : tags)
			tagsList.add(tag.textValue());
		return new Video(id, title, description, publishedAt, thumbnailUrl, tagsList, categoryId, viewCount, likeCount,
				favCount, commentCount);
	}

	@Override
	public Mono<Map<String, Video>> getVideosByIds(List<String> videoIds) {
		var joinedIds = String.join(",", videoIds);
		var url = "https://youtube.googleapis.com/youtube/v3/videos?part={parts}&id={ids}&key={key}";
		return this.http.get()//
				.uri(url, Map.of("ids", joinedIds, "key", this.apiKey, "parts", "snippet,statistics"))//
				.retrieve()//
				.bodyToFlux(JsonNode.class)//
				.flatMap(jn -> {
					var items = jn.get("items");
					var list = new ArrayList<Video>();
					for (var item : items)
						list.add(buildVideoFromJsonNode(item));
					return Flux.fromIterable(list);
				})//
				.collectMap(Video::videoId);
	}

	@Override
	public Mono<Video> getVideoById(String videoId) {
		var singleResult = this.getVideosByIds(List.of(videoId));
		return singleResult//
				.doOnNext(map -> Assert.isTrue(map.size() == 1, () -> "there should be exactly one result"))//
				.map(m -> m.get(videoId));
	}

	@SneakyThrows
	private Playlist buildPlaylistForJsonNode(JsonNode jsonNode) {
		var itemCount = jsonNode.get("contentDetails").get("itemCount").intValue();
		var playlistId = jsonNode.get("id").textValue();
		var snippet = jsonNode.get("snippet");
		var title = snippet.get("title").textValue();
		var description = snippet.get("description").textValue();
		var publishedAt = buildDateFrom(snippet.get("publishedAt").textValue());
		var channelId = snippet.get("channelId").textValue();
		return new Playlist(playlistId, channelId, publishedAt, title, description, itemCount);
	}

	@Override
	public Flux<Video> getAllVideosByPlaylist(String playlistId) {
		var expanded = getVideosByPlaylist(playlistId, null)//
				.expand(playlistVideos -> {
					var nextPageToken = playlistVideos.nextPageToken();
					if (!StringUtils.hasText(nextPageToken)) {
						return Mono.empty();
					}
					else {
						return getVideosByPlaylist(playlistId, nextPageToken);
					}
				});
		return expanded.map(PlaylistVideos::videos).flatMap(Flux::fromIterable);
	}

	// todo figure out pagination. for now, this won't matter since
	// I've only got like 40 playlists... but ONE DAY..!
	@Override
	public Flux<Playlist> getPlaylistsForChannel(String channelId) {

		var url = "https://youtube.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&channelId={channelId}&maxResults=50&key={key}";
		return this.http.get()//
				.uri(url, Map.of("channelId", channelId, "key", this.apiKey))//
				.retrieve()//
				.bodyToFlux(JsonNode.class)//
				.flatMap(jn -> {
					var list = new ArrayList<Playlist>();
					var items = jn.get("items");
					for (var i : items)
						list.add(buildPlaylistForJsonNode(i));
					return Flux.fromIterable(list);
				});
	}

	@Override
	public Mono<PlaylistVideos> getVideosByPlaylist(String playlistId, String pageToken) {

		var url = "https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&key={key}&maxResults=500&playlistId={playlistId}"
				+ (StringUtils.hasText(pageToken) ? "&pageToken={pt}" : "");
		return this.http.get()//
				.uri(url, Map.of("key", this.apiKey, "pt", pageToken + "", "playlistId", playlistId))//
				.retrieve()//
				.bodyToFlux(JsonNode.class)//
				.flatMap(jsonNode -> {//
					var items = jsonNode.get("items");
					var list = new ArrayList<String>();
					for (var item : items)
						list.add(item.get("contentDetails").get("videoId").textValue());
					return getVideosByIds(list)//
							.map(Map::values)//
							.map(videoCollection -> {
								var pageInfo = jsonNode.get("pageInfo");
								var resultsPerPage = pageInfo.get("resultsPerPage").intValue();
								var totalResults = pageInfo.get("totalResults").intValue();
								var nextPageToken = jsonNode.has("nextPageToken")
										? jsonNode.get("nextPageToken").textValue() : null;
								return new PlaylistVideos(playlistId, videoCollection, nextPageToken, null,
										resultsPerPage, totalResults);
							});

				})//
				.singleOrEmpty();
	}

	private Mono<Channel> findChannel(String username, String urlExtension, Map<String, String> params) {
		var uri = "https://youtube.googleapis.com/youtube/v3/channels?part=snippet&key={key}" + urlExtension;
		var uriVariables = new HashMap<String, String>();
		uriVariables.putAll(Map.of("username", username, "key", this.apiKey));
		uriVariables.putAll(params);
		return this.http//
				.get()//
				.uri(uri, uriVariables)//
				.retrieve()//
				.bodyToFlux(JsonNode.class)//
				.map(this::buildChannelFromJsonNode)///
				.singleOrEmpty();
	}

	@SneakyThrows
	private Channel buildChannelFromJsonNode(JsonNode jsonNode) {
		var items = jsonNode.withArray("items");
		for (var i : items) {
			Assert.isTrue(i.get("kind").textValue().equals("youtube#channel"), "the item is a YouTube channel");
			var id = i.get("id").textValue();
			var title = i.get("snippet").get("title").textValue();
			var description = i.get("snippet").get("description").textValue();
			var publishedAt = i.get("snippet").get("publishedAt").textValue();
			return new Channel(id, title, description, buildDateFrom(publishedAt));
		}
		throw new RuntimeException("we should never reach this point! there was no Channel found");
	}

	private static Date buildDateFrom(String isoDate) {
		return Date.from(Instant.parse(isoDate));
	}

	DefaultYoutubeClient(WebClient http, String apiKey) {
		this.http = http;
		this.apiKey = apiKey;
	}

}
