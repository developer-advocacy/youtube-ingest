package com.joshlong.youtube.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

	private void validateChannel(Channel channel) {
		var dateString = channel.date().toString();
		Assertions.assertTrue(channel.title().contains("Spring"));
		Assertions.assertTrue(channel.description().contains("Spring is the most"));
		Assertions.assertTrue(dateString.contains(" 2011"));
	}

}