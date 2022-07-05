package com.joshlong.youtube.client;

import com.joshlong.youtube.YoutubeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class YoutubeClientConfiguration {

	@Bean
	YoutubeClient youtubeClient(WebClient http, YoutubeProperties properties) {
		return new DefaultYoutubeClient(http, properties.youtube().apiKey());
	}

}
