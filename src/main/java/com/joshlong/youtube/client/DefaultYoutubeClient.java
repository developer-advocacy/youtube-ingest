package com.joshlong.youtube.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
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
					var is = jn.get("items");
					var list = new ArrayList<Video>();
					for (var i : is)
						list.add(buildVideoFromJsonNode(i));
					return Flux.fromIterable(list);
				}).doOnNext(v -> System.out.println(v.videoId())).collectMap(Video::videoId);
	}

	@Override
	public Mono<Video> getVideoById(String videoId) {
		var singleResult = this.getVideosByIds(List.of(videoId));
		return singleResult//
				.doOnNext(map -> Assert.isTrue(map.size() == 1, () -> "there should be exactly one result"))//
				.map(m -> m.get(videoId));
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

	DefaultYoutubeClient(WebClient http, @Value("${bootiful.youtube.api-key}") String apiKey) {
		this.http = http;
		this.apiKey = apiKey;
	}

}