package com.joshlong.youtube.batch;

import com.joshlong.youtube.YoutubeProperties;
import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.Playlist;
import com.joshlong.youtube.client.YoutubeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@EnableBatchProcessing
class IngestBatchApplication {

	@Bean
	TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
		return new TransactionTemplate(transactionManager);
	}

	@Bean
	JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	// todo read in all the videos for each of the playlists

	// read in a channel
	@Configuration
	@RequiredArgsConstructor
	static class ChannelStepConfiguration {

		private final StepBuilderFactory sbf;

		private final YoutubeClient client;

		private final YoutubeProperties properties;

		private final DataSource dataSource;

		@Bean(name = "channelStepReader")
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

		@Bean(name = "channelStepWriter")
		ItemWriter<Channel> writer() {
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

		@Bean(name = "channelStep")
		Step step() {
			return this.sbf.get("channels")//
					.<Channel, Channel>chunk(10)//
					.reader(this.reader())//
					.writer(this.writer())//
					.build();
		}

	}

	// read in all the playlists
	@Configuration
	@RequiredArgsConstructor
	static class PlaylistStepConfiguration {

		private final StepBuilderFactory sbf;

		private final TransactionTemplate transactionTemplate;

		private final YoutubeClient client;

		private final JdbcTemplate template;

		private final DataSource dataSource;

		@Bean(name = "playlistStepReader")
		ItemReader<Channel> reader() {
			return new JdbcCursorItemReaderBuilder<Channel>()//
					.name("playlistStepReader").sql("select * from yt_channels")//
					.rowMapper((rs, rowNum) -> new Channel(rs.getString("channel_id"), rs.getString("title"),
							rs.getString("description"), rs.getDate("published_at")))//
					.dataSource(this.dataSource).build();
		}

		private record ChannelPlaylists(Channel channel, List<Playlist> playlists) {
		}

		@Bean(name = "playlistStepProcessor")
		ItemProcessor<Channel, ChannelPlaylists> processor() {
			return channel -> {
				var playlists = client.getAllPlaylistsByChannel(channel.channelId()).collectList().block();
				return new ChannelPlaylists(channel, playlists);
			};
		}

		@Bean(name = "playlistStepWriter")
		ItemWriter<ChannelPlaylists> writer() {
			return items -> transactionTemplate.executeWithoutResult(tx -> {
				for (var cp : items)
					this.doWrite(template, cp);
			});
		}

		private void doWrite(JdbcTemplate template, ChannelPlaylists channelPlaylists) {

			var sql = """
					insert into yt_playlists (
					 playlist_id , channel_id, published_at, title, description, item_count
					)
					values(? , ?, ?, ?, ?, ?);
					""";
			var playlists = channelPlaylists.playlists();
			for (var playlist : playlists)
				template.update(sql, playlist.playlistId(), playlist.channelId(), playlist.publishedAt(),
						playlist.title(), playlist.description(), playlist.itemCount());
		}

		@Bean(name = "playlistStep")
		Step step() {
			return sbf.get("playlists").<Channel, ChannelPlaylists>chunk(100).reader(reader()).processor(processor())
					.writer(writer()).build();
		}

	}

	@Bean
	Job job(JobBuilderFactory jbf, ChannelStepConfiguration step1, PlaylistStepConfiguration step2) {
		return jbf.get("yt")//
				.start(step1.step())//
				.next(step2.step())//
				.incrementer(new RunIdIncrementer())//
				.build();
	}

}
