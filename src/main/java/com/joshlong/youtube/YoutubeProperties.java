package com.joshlong.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "bootiful")
public record YoutubeProperties(Youtube youtube, Batch batch) {

	public record Batch(String channelUsername) {
	}

	public record Youtube(String apiKey) {
	}
}
