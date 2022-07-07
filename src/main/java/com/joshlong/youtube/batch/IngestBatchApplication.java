package com.joshlong.youtube.batch;

import com.joshlong.youtube.YoutubeProperties;
import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@EnableBatchProcessing
class IngestBatchApplication {

	// read in information about a given YT channel
	// read in all the playlists
	// read in all the videos for each of the playlists

	@Configuration
	@RequiredArgsConstructor
	static class ChannelStepConfiguration {

		private final StepBuilderFactory sbf;

		private final YoutubeClient client;

		private final YoutubeProperties properties;

		private final DataSource dataSource;

		@Bean
		ItemReader<Channel> reader() {
			var ar = new AtomicReference<Channel>();
			return () -> {
				var channel = client.getChannelByUsername(properties.batch().channelUsername()).block();
				if (ar.compareAndSet(null, channel)) {
					return channel;
				}
				return null;
			};
		}

		@Bean
		JdbcBatchItemWriter<Channel> writer() {
			var sql = """
					    insert into yt_channels(channel_id  ,description , published_at , title)
					    values (?,?,?,?)
					    on conflict on constraint yt_channels_pkey
					    do nothing
					""";
			return new JdbcBatchItemWriterBuilder<Channel>()//
					.sql(sql)//
					.dataSource(this.dataSource)//
					.assertUpdates(false) //
					.itemPreparedStatementSetter((channel, ps) -> {
						ps.setString(1, channel.channelId());
						ps.setString(2, channel.description());
						ps.setDate(3, new java.sql.Date(channel.publishedAt().getTime()));
						ps.setString(4, channel.title());
					})//
					.build();
		}

		@Bean
		Step step() {
			return this.sbf.get("channels").<Channel, Channel>chunk(10).reader(this.reader()).writer(this.writer())
					.build();
		}

	}

	@Bean
	Job job(JobBuilderFactory jbf, ChannelStepConfiguration step1) {
		return jbf.get("yt").start(step1.step()).incrementer(new RunIdIncrementer()).build();
	}

}
