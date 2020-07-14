package me.qyh.downinsrun.parser;

import me.qyh.downinsrun.LogicException;
import org.jsoup.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Configure {

    private static final Path path = Paths.get(System.getProperty("user.home")).resolve("downins")
            .resolve("config.properties");

    private static final String SESSIONID_KEY = "downins.sessionid";
    private static final String LOCATION_KEY = "downins.location";
    private static final String THREAD_NUM_KEY = "downins.maxThreadNums";
    private static final String PROXY_ADDR_KEY = "downins.proxyAddr";
    private static final String PROXY_PORT_KEY = "downins.proxyPort";
    private static final String TIMEOUT_KEY = "downins.timeout";
    private static final String USERNAME_KEY = "downins.username";
    private static final String PASSWORD_KEY = "downins.password";

    private static final String STORY_QUERY_HASH = "downins.story.queryHash";
    private static final String CHANNEL_QUERY_HASH = "downins.channel.queryHash";
    private static final String USER_QUERY_HASH = "downins.user.queryHash";
    private static final String STORIES_QUERY_HASH = "downins.stories.queryHash";
    private static final String TAG_QUERY_HASH = "dowins.tag.queryHash";

    private final Properties pros = new Properties();

    static {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Configure ins = new Configure();

    public Configure() {
        super();
        try (InputStream is = Files.newInputStream(path)) {
            pros.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Configure get() {
        return ins;
    }

    public void store(DowninsConfig config) throws LogicException, IOException {

        if (config.getProxyPort() != null && (config.getProxyPort() < 1 || config.getProxyPort() > 65535)) {
            throw new LogicException("代理端口应该在1~65535之间(包含)");
        }

        if (config.getThreadNum() < 1 || config.getThreadNum() > 10) {
            throw new LogicException("下载线程数应该在1~10之间(包含)");
        }

        try {
            config.getDownloadDir();
        } catch (Exception e) {
            throw new LogicException("创建下载目录失败");
        }

        if (!isEmpty(config.getSid()))
            pros.setProperty(SESSIONID_KEY, config.getSid());
        else
            pros.remove(SESSIONID_KEY);
        if (!isEmpty(config.getLocation()))
            pros.setProperty(LOCATION_KEY, config.getLocation());
        else
            pros.remove(LOCATION_KEY);
        if (config.getThreadNum() > 0 && config.getThreadNum() <= 20)
            pros.setProperty(THREAD_NUM_KEY, String.valueOf(config.getThreadNum()));
        if (!isEmpty(config.getProxyAddr()))
            pros.setProperty(PROXY_ADDR_KEY, config.getProxyAddr());
        else
            pros.remove(PROXY_ADDR_KEY);
        if (config.getProxyPort() != null)
            pros.setProperty(PROXY_PORT_KEY, String.valueOf(config.getProxyPort()));
        else
            pros.remove(PROXY_PORT_KEY);
        if (!isEmpty(config.getStoryQueryHash()))
            pros.setProperty(STORY_QUERY_HASH, config.getStoryQueryHash());
        else
            pros.remove(STORY_QUERY_HASH);
        if (!isEmpty(config.getChannelQueryHash()))
            pros.setProperty(CHANNEL_QUERY_HASH, config.getChannelQueryHash());
        else
            pros.remove(CHANNEL_QUERY_HASH);
        if (!isEmpty(config.getUserQueryHash()))
            pros.setProperty(USER_QUERY_HASH, config.getUserQueryHash());
        else
            pros.remove(USER_QUERY_HASH);
        if (!isEmpty(config.getStoriesQueryHash()))
            pros.setProperty(STORIES_QUERY_HASH, config.getStoriesQueryHash());
        else
            pros.remove(STORIES_QUERY_HASH);
        if (!isEmpty(config.getTagQueryHash()))
            pros.setProperty(TAG_QUERY_HASH, config.getTagQueryHash());
        else
            pros.remove(TAG_QUERY_HASH);
        if (!isEmpty(config.getUsername()))
            pros.setProperty(USERNAME_KEY, config.getUsername());
        else
            pros.remove(USERNAME_KEY);

        if (!isEmpty(config.getPassword()))
            pros.setProperty(PASSWORD_KEY, config.getPassword());
        else
            pros.remove(PASSWORD_KEY);
        try (OutputStream os = Files.newOutputStream(path)) {
            pros.store(os, "");
        }
        try (InputStream is = Files.newInputStream(path)) {
            pros.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public DowninsConfig getConfig() {
        DowninsConfig config = new DowninsConfig();
        config.setSid(pros.getProperty(SESSIONID_KEY));
        config.setThreadNum(Integer.valueOf(pros.getProperty(THREAD_NUM_KEY, "10")));
        config.setLocation(
                pros.getProperty(LOCATION_KEY, System.getProperty("user.home") + File.separator + "downins"));
        config.setProxyAddr(pros.getProperty(PROXY_ADDR_KEY));
        config.setStoryQueryHash(pros.getProperty(STORY_QUERY_HASH));
        String port = pros.getProperty(PROXY_PORT_KEY);
        if (port != null) {
            config.setProxyPort(Integer.parseInt(port));
        }
        config.setChannelQueryHash(pros.getProperty(CHANNEL_QUERY_HASH));
        config.setStoriesQueryHash(pros.getProperty(STORIES_QUERY_HASH));
        config.setUserQueryHash(pros.getProperty(USER_QUERY_HASH));
        config.setTagQueryHash(pros.getProperty(TAG_QUERY_HASH));
        config.setUsername(pros.getProperty(USERNAME_KEY));
        config.setPassword(pros.getProperty(PASSWORD_KEY));
        return config;
    }

    public int getTimeout() {
        String timeout = pros.getProperty(TIMEOUT_KEY);
        if (timeout != null) {
            return Integer.parseInt(timeout);
        }
        return 60 * 1000;
    }

}
