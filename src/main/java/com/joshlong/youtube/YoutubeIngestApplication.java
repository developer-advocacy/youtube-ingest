package com.joshlong.youtube;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableConfigurationProperties(YoutubeProperties.class)
public class YoutubeIngestApplication {

	public static void main(String[] args) {
		SpringApplication.run(YoutubeIngestApplication.class, args);
	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

}
