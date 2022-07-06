package com.joshlong.youtube.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

@Slf4j
@SpringBootTest
class DefaultYoutubeClientTest {

	private final String channelId = "UC7yfnfvEUlXUIfm8rGLwZdA";

	private final YoutubeClient youtubeClient;

	DefaultYoutubeClientTest(@Autowired YoutubeClient youtubeClient) {
		this.youtubeClient = youtubeClient;
	}

	@Test
	void videosByPlaylist() throws Exception {
		var playlistId = "PLgGXSWYM2FpPw8rV0tZoMiJYSCiLhPnOc";
		var videos = this.youtubeClient.getVideosByPlaylist(playlistId).map(pv -> pv.videos().size());
		StepVerifier//
				.create(videos)//
				.expectNext(50).verifyComplete();
	}

	@Test
	void playlistsByChannel() throws Exception {
		var playlists = this.youtubeClient.getPlaylistsForChannel(this.channelId);
		StepVerifier.create(playlists)//
				.expectNextMatches(playlist -> StringUtils.hasText(playlist.playlistId())
						&& playlist.channelId().equals(channelId) && playlist.itemCount() > 0
						&& playlist.publishedAt() != null && StringUtils.hasText(playlist.title()))
				.expectNextCount(28)//
				.verifyComplete();
	}

	@Test
	void channelByUsername() throws Exception {
		var channel = this.youtubeClient.getChannelByUsername("springsourcedev");
		validateChannel(channel);
	}

	@Test
	void channelByChannelId() throws Exception {
		var channel = this.youtubeClient.getChannelById(this.channelId);
		validateChannel(channel);
	}

	@Test
	void videosById() throws Exception {
		var videosMap = this.youtubeClient.getVideosByIds(List.of("ahBjkmkltcc", "EE-5xItDfsg"));
		StepVerifier.create(videosMap).expectNextMatches(m -> m.size() == 2).verifyComplete();
	}

	@Test
	void videoById() throws Exception {
		var video = this.youtubeClient.getVideoById("eIho2S0ZahI");
		StepVerifier.create(video)//
				.expectNextMatches(result -> result.videoId().equalsIgnoreCase("eIho2S0ZahI")
						&& !result.tags().isEmpty() && result.tags().contains("Julian Treasure")
						&& result.viewCount() >= 13852996 && result.commentCount() >= 9596
						&& result.likeCount() >= 354551 && result.favoriteCount() >= 0
						&& result.title().contains("How to speak so that people want to listen"))
				.verifyComplete();

	}

	private void validateChannel(Mono<Channel> channel) {
		StepVerifier//
				.create(channel)//
				.expectNextMatches(channel1 -> {//
					var dateString = channel1.date().toString();
					return (channel1.title().contains("Spring"))
							&& (channel1.description().contains("Spring is the most"))
							&& (dateString.contains(" 2011"));
				}).verifyComplete();
	}

}