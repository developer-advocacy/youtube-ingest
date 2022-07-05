package com.joshlong.youtube.client;

/**
 * A simple YouTube client
 *
 * @author Josh Long
 */
public interface YoutubeClient {

	/**
	 * Finds a youtube channel by the username that created it.
	 * @param username a username, like {@code SpringDeveloper}
	 * @return a {@link Channel channel} that contains the metadata for a given Youtube
	 * channel
	 */
	Channel getChannelByUsername(String username);

	/**
	 * Finds a youtube channel by the channel ID associated with the username that created
	 * it
	 * @param channelId a channel ID (each YouTube channel can be find by a username or an
	 * ID)
	 * @return a {@link Channel channel} that contains the metadata for a given Youtube
	 * channel
	 */
	Channel getChannelById(String channelId);

}
