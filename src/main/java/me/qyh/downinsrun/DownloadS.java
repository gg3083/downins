package me.qyh.downinsrun;

import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.Https;
import me.qyh.downinsrun.parser.InsParser;
import me.qyh.downinsrun.parser.InsParser.Url;
import me.qyh.downinsrun.parser.ParseUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 下载某一个Story
 *
 * @author wwwqyhme
 */
class DownloadS {

    private final String id;
    private final Path errorF;
    private final Path root;
    private final Path tempDir;
    private final CloseableHttpClient client = Https.newHttpClient();
    private final InsParser parser = new InsParser(false, client);

    public DownloadS(String id, Path dir) throws Exception {
        super();
        ParseUtils.trySetSid(client);
        this.id = id;
        this.root = dir.resolve(id);
        try {
            Files.createDirectories(root);
        } catch (Throwable e) {
            System.out.println("创建文件夹:" + root + "失败");
            System.exit(-1);
        }
        this.tempDir = root.resolve("temp");
        try {
            Files.createDirectories(tempDir);
        } catch (Exception e) {
            System.out.println("创建文件夹:" + tempDir + "失败");
            System.exit(-1);
        }
        this.errorF = root.resolve("error_f.json");
        if (!Files.exists(errorF)) {
            try {
                Files.createFile(errorF);
            } catch (Throwable e) {
                System.out.println("创建文件:" + errorF + "失败");
                System.exit(-1);
            }
        }
    }

    public void start() {
        // 下载失败文件
        DownloadF.downloadError(parser, client, errorF, tempDir);

        List<Url> urls = new ArrayList<>();
        boolean error = false;
        try {
            urls = parser.parseStory(id).values().stream().flatMap(s -> s.stream()).collect(Collectors.toList());
        } catch (LogicException e) {
            System.out.println(e.getMessage());
            System.exit(-1);
        } catch (Throwable e) {

            error = true;
        }
        if (!error) {
            error = urls.isEmpty();
        }
        if (error) {
            System.out.println("获取story内容失败");
            System.exit(-1);
        }
        ExecutorService es = Executors
                .newFixedThreadPool(Math.min(Configure.get().getConfig().getThreadNum(), urls.size()));
        int index = 0;
        for (Url url : urls) {
            String name = (++index) + "." + Utils.getFileExtension(url.getValue());
            es.execute(() -> {
                DownloadF df = new DownloadF(errorF, url.getValue(), root.resolve(name), client, tempDir, null, -1,
                        null);
                df.start();
            });
        }
        es.shutdown();
        try {
            es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.out.println("下载story" + id + "失败");
            System.exit(-1);
        }

        try {
            String errorFContent = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
            if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
                Files.delete(errorF);
            }
        } catch (Throwable e) {
        }

        DownloadF.downloadError(parser, client, errorF, tempDir);

        try {
            String errorFContent = new String(Files.readAllBytes(errorF));
            if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
                Files.delete(errorF);
            }
        } catch (Throwable e) {
        }
        try {
            client.close();
        } catch (IOException e) {
        }
        Utils.deleteDir(tempDir);
        System.out.println("下载story" + id + "完成，文件存储目录:" + root.toString());
    }
}
