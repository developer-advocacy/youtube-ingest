package com.joshlong.youtube.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class DefaultYoutubeClientTest {

	private final String channelId = "UC7yfnfvEUlXUIfm8rGLwZdA";

	private final String playlistId = "PLgGXSWYM2FpPw8rV0tZoMiJYSCiLhPnOc";

	private final YoutubeClient youtubeClient;

	DefaultYoutubeClientTest(@Autowired YoutubeClient youtubeClient) {
		this.youtubeClient = youtubeClient;
	}

	@Test
	void allVideosByChannel() throws Exception {
		var all = this.youtubeClient.getChannelByUsername("starbuxman")
				.flatMapMany(channel -> this.youtubeClient.getAllVideosByChannel(channel.channelId()));
		StepVerifier//
				.create(all.collectList().map(List::size))//
				.expectNextMatches(count -> count >= 25)//
				.verifyComplete();
	}

	@Test
	void videosByChannel() throws Exception {
		var all = this.youtubeClient.getChannelByUsername("starbuxman")
				.flatMapMany(channel -> this.youtubeClient.getVideosByChannel(channel.channelId(), null));
		StepVerifier//
				.create(all.map(cv -> cv.videos().size()))//
				.expectNextMatches(count -> count == 20)//
				.verifyComplete();
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
		var channelName = "GoogleDevelopers";
		var playlists = this.youtubeClient.getChannelByUsername(channelName)
				.flatMapMany(channel -> this.youtubeClient.getAllPlaylistsByChannel(channel.channelId()));
		StepVerifier//
				.create(playlists.collectList().map(HashSet::new).map(Set::size))//
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
					var dateString = channelToMatch.publishedAt().toString();
					return (channelToMatch.title().contains("Spring"))
							&& (channelToMatch.description().contains("Spring is the most"))
							&& (dateString.contains(" 2011"));
				})//
				.verifyComplete();
	}

}