package com.joshlong.youtube.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
class DefaultYoutubeClient implements YoutubeClient {

	private final WebClient http;

	private final String apiKey;

	@Override
	public Channel getChannelByUsername(String username) {
		return findChannel(username, "&forUsername={username}", Map.of("username", username));
	}

	@Override
	public Channel getChannelById(String channelId) {
		return findChannel(channelId, "&id={id}", Map.of("id", channelId));
	}

	private Channel findChannel(String username, String urlExtension, Map<String, String> params) {
		var uri = "https://youtube.googleapis.com/youtube/v3/channels?part=snippet&key={key}" + urlExtension;
		var uriVariables = new HashMap<String, String>();
		uriVariables.putAll(Map.of("username", username, "key", this.apiKey));
		uriVariables.putAll(params);
		return this.http.get().uri(uri, uriVariables).retrieve().bodyToFlux(JsonNode.class)
				.map(this::buildChannelFromJsonNode).toIterable().iterator().next();
	}

	@SneakyThrows
	private Channel buildChannelFromJsonNode(JsonNode jsonNode) {
		var items = jsonNode.withArray("items");
		for (var i : items) {
			Assert.isTrue(i.get("kind").textValue().equals("youtube#channel"), () -> "the item is a YouTube channel");
			var id = i.get("id").textValue();
			var title = i.get("snippet").get("title").textValue();
			var description = i.get("snippet").get("description").textValue();
			var publishedAt = i.get("snippet").get("publishedAt").textValue();
			var instantPublishedAt = Instant.parse(publishedAt);
			return new Channel(id, title, description, Date.from(instantPublishedAt));
		}
		throw new RuntimeException("we should never reach this point! there was no Channel found");
	}

	DefaultYoutubeClient(WebClient http, @Value("${bootiful.youtube.api-key}") String apiKey) {
		this.http = http;
		this.apiKey = apiKey;
	}

}
