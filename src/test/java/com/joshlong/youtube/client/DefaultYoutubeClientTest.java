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

	private final String playlistId = "PLgGXSWYM2FpPw8rV0tZoMiJYSCiLhPnOc";

	private final YoutubeClient youtubeClient;

	DefaultYoutubeClientTest(@Autowired YoutubeClient youtubeClient) {
		this.youtubeClient = youtubeClient;
	}

	@Test
	void allVideosByPlaylist() throws Exception {
		var all = this.youtubeClient.getAllVideosByPlaylist(this.playlistId);
		StepVerifier.create(all//
				.doOnNext(video -> log.info(video.title() + " / " + video.publishedAt())) //
				.collectList()//
				.map(List::size)//
		)//
				.expectNextMatches(count -> count >= 100).verifyComplete();
	}

	@Test
	void videosByPlaylist() throws Exception {

		var videos = this.youtubeClient.getVideosByPlaylist(playlistId, null).map(pv -> pv.videos().size());
		StepVerifier.create(videos).expectNext(50).verifyComplete();

		var nextVideos = this.youtubeClient.getVideosByPlaylist(playlistId, "EAAaBlBUOkNESQ")
				.map(m -> m.videos().size());
		StepVerifier.create(nextVideos).expectNext(50).verifyComplete();
	}

	@Test
	void allPlaylistsByChannel() throws Exception {

		var channelName = "GoogleChromeDevelopers";
		var playlists = this.youtubeClient.getChannelById(this.channelId)
				.flatMapMany(channel -> this.youtubeClient.getAllPlaylistsByChannel(channel.channelId()));

		StepVerifier//
				.create(playlists.collectList().map(List::size))//
				.expectNextMatches(count -> count > 20)//
				.verifyComplete();
	}

	@Test
	void playlistsByChannel() throws Exception {
		var playlists = this.youtubeClient.getPlaylistsByChannel(this.channelId, null);
		StepVerifier//
				.create(playlists.flatMapIterable(ChannelPlaylists::playlists))//
				.expectNextMatches(playlist -> StringUtils.hasText(playlist.playlistId())
						&& playlist.channelId().equals(channelId) && playlist.itemCount() > 0
						&& playlist.publishedAt() != null && StringUtils.hasText(playlist.title()))
				.thenConsumeWhile(p -> true).verifyComplete();
	}

	@Test
	void channelByUsername() throws Exception {
		var channel = this.youtubeClient.getChannelByUsername("springsourcedev");
		validateSpringChannel(channel);

		var googleChannel = this.youtubeClient.getChannelByUsername("GoogleDevelopers");
		StepVerifier.create(googleChannel).expectNextMatches(
				c -> c.title().toLowerCase().contains("google") && c.description().toLowerCase().contains("google"))
				.verifyComplete();
	}

	@Test
	void channelByChannelId() throws Exception {
		var channel = this.youtubeClient.getChannelById(this.channelId);
		validateSpringChannel(channel);
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
						&& result.viewCount() >= 13852000 && result.commentCount() >= 9000
						&& result.likeCount() >= 354000 && result.favoriteCount() >= 0
						&& result.title().contains("How to speak so that people want to listen"))
				.verifyComplete();

	}

	private void validateSpringChannel(Mono<Channel> channel) {
		StepVerifier//
				.create(channel)//
				.expectNextMatches(channelToMatch -> {//
					var dateString = channelToMatch.date().toString();
					return (channelToMatch.title().contains("Spring"))
							&& (channelToMatch.description().contains("Spring is the most"))
							&& (dateString.contains(" 2011"));
				})//
				.verifyComplete();
	}

}