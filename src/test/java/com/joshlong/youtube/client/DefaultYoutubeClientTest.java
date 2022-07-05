package com.joshlong.youtube.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

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
	void videoById() throws Exception {
		var video = this.youtubeClient.getVideoById("eIho2S0ZahI");
		Assert.isTrue(video.videoId().equalsIgnoreCase("eIho2S0ZahI"), "you don't have a valid video ID");
		Assert.isTrue(!video.tags().isEmpty(), "there should be more than zero tags");
		Assert.isTrue(video.tags().contains("Julian Treasure"), "the tags should include the speaker");
		Assert.isTrue(video.viewCount() >= 13852996, "the view count should line up with what's on YT'");
		Assert.isTrue(video.commentCount() >= 9596, "the comment count should line up with what's on YT'");
		Assert.isTrue(video.likeCount() >= 354551, "the like count should line up with what's on YT'");
		Assert.isTrue(video.favoriteCount() >= 0, "the favorite count should line up with what's on YT'");
		Assert.isTrue(video.title().contains("How to speak so that people want to listen"),
				"the title must reflect what's shown on YT");
	}

	private void validateChannel(Channel channel) {
		var dateString = channel.date().toString();
		Assertions.assertTrue(channel.title().contains("Spring"));
		Assertions.assertTrue(channel.description().contains("Spring is the most"));
		Assertions.assertTrue(dateString.contains(" 2011"));
	}

}