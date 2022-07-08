package com.joshlong.youtube.batch;

import com.joshlong.youtube.YoutubeProperties;
import com.joshlong.youtube.client.Channel;
import com.joshlong.youtube.client.Playlist;
import com.joshlong.youtube.client.Video;
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
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Objects;
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

	// Reset fresh flag. Everything will be marked fresh = false and only the stuff
	// newly read from Youtube API will be marked fresh = true.
	@Configuration
	@RequiredArgsConstructor
	static class ResetStepConfiguration {

		private final StepBuilderFactory sbf;

		private final JdbcTemplate template;

		private final TransactionTemplate transactionTemplate;

		@Bean(name = "resetStep")
		Step step() {
			return this.sbf//
					.get("reset")//
					.tasklet((stepContribution, chunkContext) -> {
						transactionTemplate.execute(status -> {
							for (var tn : "yt_playlist_videos,yt_channels,yt_playlists,yt_videos".split(","))
								template.update("update " + tn + " set fresh = false");
							return null;
						});
						return RepeatStatus.FINISHED;
					})//
					.build();
		}

	}

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
					    insert into yt_channels(channel_id  ,description , published_at , title, fresh)
					    values (?,?,?,?, true )
					    on conflict on constraint yt_channels_pkey
					    do update SET fresh = true where yt_channels.channel_id = ?
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
						ps.setString(5, channel.channelId());
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
					.name("playlistStepReader")//
					.sql("select * from yt_channels")//
					.rowMapper((rs, rowNum) -> new Channel(rs.getString("channel_id"), rs.getString("title"),
							rs.getString("description"), rs.getDate("published_at"))//
					)//
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
					doWrite(template, cp);
			});
		}

		private void doWrite(JdbcTemplate template, ChannelPlaylists channelPlaylists) {
			var sql = """
					insert into yt_playlists (
					    playlist_id,
					    channel_id,
					    published_at,
					    title,
					    description,
					    item_count,
					    fresh
					)
					values(? , ?, ?, ?, ?, ? ,? )
					on conflict on constraint yt_playlists_pkey
					do update SET fresh = true where yt_playlists.playlist_id = ?
					""";
			var playlists = channelPlaylists.playlists();
			playlists.forEach(playlist -> template.update(sql, playlist.playlistId(), playlist.channelId(),
					playlist.publishedAt(), playlist.title(), playlist.description(), playlist.itemCount(), true,
					playlist.playlistId()));
		}

		@Bean(name = "playlistStep")
		Step step() {
			return sbf.get("playlists")//
					.<Channel, ChannelPlaylists>chunk(100)//
					.reader(reader())//
					.processor(processor())//
					.writer(writer())//
					.build();
		}

	}

	// read in all the videos for each of the playlists
	@Configuration
	@RequiredArgsConstructor
	static class PlaylistVideosStepConfiguration {

		private final StepBuilderFactory sbf;

		private final TransactionTemplate transactionTemplate;

		private final YoutubeClient client;

		private final JdbcTemplate template;

		private final DataSource dataSource;

		@Bean(name = "playlistVideosStepReader")
		ItemReader<Playlist> reader() {
			return new JdbcCursorItemReaderBuilder<Playlist>()//
					.name("videoStepReader")//
					.sql("select * from yt_playlists")//
					.rowMapper((rs, rowNum) -> new Playlist(rs.getString("playlist_id"), rs.getString("channel_id"),
							new Date(rs.getDate("published_at").getTime()), rs.getString("title"),
							rs.getString("description"), rs.getInt("item_count")))//
					.dataSource(this.dataSource)//
					.build();
		}

		@Bean(name = "playlistVideosStepProcessor")
		ItemProcessor<Playlist, PlaylistVideos> processor() {
			return playlist -> {
				var videoList = this.client.getAllVideosByPlaylist(playlist.playlistId()).collectList().block();
				return new PlaylistVideos(playlist, videoList);
			};
		}

		@Bean(name = "playlistVideosStepWriter")
		ItemWriter<PlaylistVideos> writer() {
			return new PlaylistVideosItemWriter(this.transactionTemplate, this.template);
		}

		@Bean(name = "playlistVideosStep")
		Step step() {
			return sbf.get("playlistVideos")//
					.<Playlist, PlaylistVideos>chunk(100)//
					.reader(reader())//
					.processor(processor())//
					.writer(writer())//
					.build();
		}

	}

	@Configuration
	@RequiredArgsConstructor
	static class ChannelVideosStepConfiguration {

		private final YoutubeClient client;

		private final YoutubeProperties properties;

		private final JdbcTemplate jdbcTemplate;

		private final TransactionTemplate transactionTemplate;

		private final StepBuilderFactory sbf;

		@Bean(name = "channelVideosReader")
		ItemReader<Video> reader() {
			var itemReaderAtomicReference = new AtomicReference<ItemReader<Video>>();
			return () -> {
				if (itemReaderAtomicReference.get() == null) {

					var data = client//
							.getChannelByUsername(properties.batch().channelUsername())//
							.flatMapMany(channel -> client.getAllVideosByChannel(channel.channelId()))//
							.collectList()//
							.block();
					var listItemReader = new ListItemReader<>(Objects.requireNonNull(data));
					itemReaderAtomicReference.set(listItemReader);
				}
				return itemReaderAtomicReference.get().read();
			};
		}

		@Bean(name = "channelVideosWriter")
		ItemWriter<Video> writer() {
			return new VideoItemWriter(this.transactionTemplate, this.jdbcTemplate);
		}

		@Bean(name = "channelVideosStep")
		Step step() {
			return this.sbf.get("channelVideos").<Video, Video>chunk(100).reader(reader()).writer(writer()).build();
		}

	}

	@Bean
	Job job(JobBuilderFactory jbf, ResetStepConfiguration resetStepConfiguration,
			ChannelStepConfiguration channelStepConfiguration, PlaylistStepConfiguration playlistStepConfiguration,
			PlaylistVideosStepConfiguration videoStepConfiguration,
			ChannelVideosStepConfiguration channelVideosStepConfiguration) {
		return jbf.get("yt")//
				.start(resetStepConfiguration.step())//
				.next(channelStepConfiguration.step())//
				.next(playlistStepConfiguration.step())//
				.next(videoStepConfiguration.step())//
				.next(channelVideosStepConfiguration.step())//
				.incrementer(new RunIdIncrementer())//
				.build();
	}

	static record PlaylistVideos(Playlist playlist, List<Video> videos) {
	}

	static class PlaylistVideosItemWriter implements ItemWriter<PlaylistVideos> {

		private final TransactionTemplate transactionTemplate;

		private final JdbcTemplate jdbcTemplate;

		private final VideoItemWriter videoItemWriter;

		PlaylistVideosItemWriter(TransactionTemplate transactionTemplate, JdbcTemplate jdbcTemplate) {
			this.transactionTemplate = transactionTemplate;
			this.jdbcTemplate = jdbcTemplate;
			this.videoItemWriter = new VideoItemWriter(this.transactionTemplate, this.jdbcTemplate);
		}

		@Override
		public void write(List<? extends PlaylistVideos> playlistVideosList) throws Exception {
			var playlistVideoSql = """
					insert into yt_playlist_videos(
						playlist_id, video_id , fresh
					)
					values(?,? ,true )
					on conflict on constraint yt_playlist_videos_pkey
					do update  set fresh = true
					""";

			for (var playlistVideos : playlistVideosList) {
				var videoList = playlistVideos.videos();
				videoList.forEach(video -> this.jdbcTemplate.update(playlistVideoSql,
						playlistVideos.playlist().playlistId(), video.videoId()));
				this.videoItemWriter.write(videoList);
			}
		}

	}

	@RequiredArgsConstructor
	static class VideoItemWriter implements ItemWriter<Video> {

		private final TransactionTemplate transactionTemplate;

		private final JdbcTemplate jdbcTemplate;

		private void doWrite(JdbcTemplate template, List<? extends Video> videos) {
			var videoSql = """
					              insert into yt_videos (
					                  video_id ,
					                  title,
					                  description,
					                  published_at ,
					                  standard_thumbnail,
					                  category_id,
					                  view_count,
					                  favorite_count,
					                  comment_count  ,
					                  like_count ,
					                  fresh
					              )
					              values ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ? , true )
					              on conflict on CONSTRAINT yt_videos_pkey
					              do update set
					                  fresh = true,
					video_id  = excluded.video_id,
					                  title = excluded.title,
					                  description = excluded.description,
					                  published_at  = excluded.published_at,
					                  standard_thumbnail = excluded.standard_thumbnail,
					                  category_id = excluded.category_id,
					                  view_count = excluded.view_count,
					                  favorite_count = excluded.favorite_count,
					                  comment_count   = excluded.comment_count,
					                  like_count =  excluded.like_count
					               where
					                  yt_videos.video_id = ?
					              """;

			videos.forEach(video -> {

				template.update(videoSql, video.videoId(), video.title(), video.description(), video.publishedAt(),
						video.standardThumbnail().toExternalForm(), video.categoryId(), video.viewCount(),
						video.favoriteCount(), video.commentCount(), video.likeCount(), video.videoId());
			});

		}

		@Override
		public void write(List<? extends Video> items) throws Exception {
			this.transactionTemplate.executeWithoutResult(tx -> {
				this.doWrite(this.jdbcTemplate, items);
			});
		}

	}

}
