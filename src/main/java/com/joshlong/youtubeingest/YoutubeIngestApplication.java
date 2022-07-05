package com.joshlong.youtubeingest;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@SpringBootApplication
public class YoutubeIngestApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeIngestApplication.class, args);
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}

interface YoutubeClient {

    Channel getChannelByUsername(String channelId);
}

record Channel(String channelId, String title, String description, Date date) {
}

record Video() {
}

record Playlist() {
}

@Slf4j
@Component
class DefaultYoutubeClient implements YoutubeClient {

    private final WebClient http;
    private final String apiKey;

    @Override
    public Channel getChannelByUsername(String username) {
        var uri = "https://youtube.googleapis.com/youtube/v3/channels?part=snippet&key={key}&forUsername={username}";
        return this.http.get()
                .uri(uri, Map.of("username", username, "key", this.apiKey))
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(this::buildChannelFromJsonNode)
                .toIterable()
                .iterator()
                .next();
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
