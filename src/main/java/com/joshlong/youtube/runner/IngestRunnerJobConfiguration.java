package com.joshlong.youtube.runner;

import com.joshlong.youtube.YoutubeIngestApplication;
import com.joshlong.youtube.YoutubeProperties;
import com.joshlong.youtube.client.YoutubeClient;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.util.concurrent.CountDownLatch;

@Configuration
class IngestRunnerJobConfiguration {

	@Bean
	ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory cf) {
		return new R2dbcTransactionManager(cf);
	}

	@Bean
	TransactionalOperator transactionalOperator(ReactiveTransactionManager rtm) {
		return TransactionalOperator.create(rtm);
	}

	@Bean
	YoutubeIngestJobRunner youtubeIngestJobRunner(YoutubeClient client, DatabaseClient databaseClient,
			YoutubeProperties properties) {
		return new YoutubeIngestJobRunner(client, databaseClient, properties.batch().channelUsername());
	}

}
