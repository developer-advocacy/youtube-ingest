package com.joshlong.youtube.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Slf4j
@SpringBootTest
class DefaultYoutubeClientTest {

	private final YoutubeClient youtubeClient;

	DefaultYoutubeClientTest(@Autowired YoutubeClient youtubeClient) {
		this.youtubeClient = youtubeClient;
	}

	@Test
	void channelByUsername() throws Exception {
		var channel = this.youtubeClient.getChannelByUsername("springsourcedev");
		validateChannel(channel);
	}

	@Test
	void channelByChannelId() throws Exception {
		var channel = this.youtubeClient.getChannelById("UC7yfnfvEUlXUIfm8rGLwZdA");
		validateChannel(channel);
	}

	@Test
	void videosById() throws Exception {
		var videosMap = this.youtubeClient.getVideosByIds(List.of("Ks-_Mh1QhMc", "Cc0KYU2j0TM4", "CeIho2S0ZahI"));
		Assertions.assertEquals(videosMap.size(), 3);
	}

	@Test
	void videoById() throws Exception {
		var video = this.youtubeClient.getVideoById("eIho2S0ZahI");
		Assertions.assertTrue(video.videoId().equalsIgnoreCase("eIho2S0ZahI"), "you don't have a valid video ID");
		Assertions.assertTrue(!video.tags().isEmpty(), "there should be more than zero tags");
		Assertions.assertTrue(video.tags().contains("Julian Treasure"), "the tags should include the speaker");
		Assertions.assertTrue(video.viewCount() >= 13852996, "the view count should line up with what's on YT'");
		Assertions.assertTrue(video.commentCount() >= 9596, "the comment count should line up with what's on YT'");
		Assertions.assertTrue(video.likeCount() >= 354551, "the like count should line up with what's on YT'");
		Assertions.assertTrue(video.favoriteCount() >= 0, "the favorite count should line up with what's on YT'");
		Assertions.assertTrue(video.title().contains("How to speak so that people want to listen"),
				"the title must reflect what's shown on YT");
	}

	private void validateChannel(Channel channel) {
		var dateString = channel.date().toString();
		Assertions.assertTrue(channel.title().contains("Spring"));
		Assertions.assertTrue(channel.description().contains("Spring is the most"));
		Assertions.assertTrue(dateString.contains(" 2011"));
	}

}