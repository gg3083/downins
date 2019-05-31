package me.qyh.downinsrun;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import javax.swing.SwingUtilities;

import me.qyh.downinsrun.parser.InsParser;

public class Downloader {

	private static final Path config = Paths.get(System.getProperty("user.home")).resolve("downins")
			.resolve("config.properties");

	static {
		try {
			Files.createDirectories(config.getParent());
			if (!Files.exists(config)) {
				Files.createFile(config);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0 || args[0].equalsIgnoreCase("l")) {
			System.out.println("u username|url [maxFilesInDir] 根据用户名或者用户主页地址下载用户的全部帖子文件");
			System.out.println("p shortcode|url 根据帖子shortcode或者帖子地址下载帖子内全部文件");
			System.out.println("t tag|url [maxFilesInDir] 根据帖子标签名或者标签页地址下载标签内全部文件");
			System.out.println("i shortcode|url 根据IGTV shortcode或者IGTV地址下载视频文件");
			System.out.println("c username|url [maxFilesInDir] 根据用户名或者用户主页地址下载channel全部文件");
			System.out.println("_s storyid|url 根据storyid或者story地址下载story全部文件");
			System.out.println("ss username|url 根据用户名或者用户主页地址下载用户的全部story文件");
			System.out.println("s 开启一个设置面板(如果支持GUI)");
			System.exit(0);
		}
		String type = args[0];
		if (!type.equalsIgnoreCase("u") && !type.equalsIgnoreCase("p") && !type.equalsIgnoreCase("t")
				&& !type.equalsIgnoreCase("i") && !type.equalsIgnoreCase("c") && !type.equalsIgnoreCase("_s")
				&& !type.equalsIgnoreCase("ss")) {

			if (type.equalsIgnoreCase("s")) {
				processSetting(args);
				return;
			}
			prtError("无效的类型，只支持用户|帖子|标签|IGTV|channel的下载");
		}
		if (args.length < 2) {
			prtError("无效的参数");
		}
		String url = args[1];
		Optional<String> opsign = null;
		if (type.equalsIgnoreCase("u")) {
			opsign = InsParser.getUsername(url);
		}
		if (type.equalsIgnoreCase("p")) {
			opsign = InsParser.getShortcode(url);
		}
		if (type.equalsIgnoreCase("t")) {
			opsign = InsParser.getTag(url);
		}
		if (type.equalsIgnoreCase("i")) {
			opsign = InsParser.getIgtvShortcode(url);
		}
		if (type.equalsIgnoreCase("c")) {
			opsign = InsParser.getChannel(url);
		}
		if (type.equalsIgnoreCase("ss")) {
			opsign = InsParser.getUsername(url);
		}
		if (type.equalsIgnoreCase("_s")) {
			opsign = InsParser.getStoryId(url);
		}
		if (opsign == null || !opsign.isPresent()) {
			prtError("无法解析的地址或标识");
		}

		Path dir = Configure.get().getConfig().getDownloadDir();
		String sign = opsign.get();
		System.out.println("开始执行任务");
		long start = System.currentTimeMillis();
		if ("p".equalsIgnoreCase(type)) {
			new DownloadP(sign, dir).start();
		}
		if ("i".equalsIgnoreCase(type)) {
			new DownloadI(sign, dir).start();
		}
		if ("ss".equalsIgnoreCase(type)) {
			new DownloadSS(sign, dir).start();
		}
		if ("_s".equalsIgnoreCase(type)) {
			new DownloadS(sign, dir).start();
		}
		if ("u".equalsIgnoreCase(type)) {
			if (args.length == 3) {
				try {
					new DownloadU(sign, dir, Integer.parseInt(args[2])).start();
				} catch (NumberFormatException e) {
					System.out.println("文件夹内最大文件数目必须为一个数字");
				}
			} else {
				new DownloadU(sign, dir, Integer.MAX_VALUE).start();
			}
		}
		if ("t".equalsIgnoreCase(type)) {
			if (args.length == 3) {
				try {
					new DownloadT(sign, dir, Integer.parseInt(args[2])).start();
				} catch (NumberFormatException e) {
					System.out.println("文件夹内最大文件数目必须为一个数字");
				}
			} else {
				new DownloadT(sign, dir, Integer.MAX_VALUE).start();
			}
		}

		if ("c".equalsIgnoreCase(type)) {
			if (args.length == 3) {
				try {
					new DownloadC(sign, dir, Integer.parseInt(args[2])).start();
				} catch (NumberFormatException e) {
					System.out.println("文件夹内最大文件数目必须为一个数字");
				}
			} else {
				new DownloadC(sign, dir, Integer.MAX_VALUE).start();
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("任务执行完毕，共计耗时:" + (end - start) / 1000 + "s，系统即将退出");
		System.exit(0);
	}

	private static final String[] VALID_KEYS = { "threadNum", "location", "proxyPort", "proxyAddr", "sid" };
	private static final StringBuilder validKeysString = new StringBuilder();

	static {
		for (String vk : VALID_KEYS) {
			validKeysString.append(vk).append(",");
		}
		validKeysString.deleteCharAt(validKeysString.length() - 1);
	}

	private static boolean isValidKey(String key) {
		for (String vk : VALID_KEYS) {
			if (key.equalsIgnoreCase(vk)) {
				return true;
			}
		}
		return false;
	}

	private static void processSetting(String[] args) {
		if (GraphicsEnvironment.isHeadless()) {
			if (args.length == 1) {
				System.exit(0);
			}
			DowninsConfig config = new DowninsConfig();

			boolean setThreadNum = false;
			boolean setLocation = false;
			boolean setProxyAddr = false;
			boolean setProxyPort = false;
			boolean setSid = false;

			for (int i = 1; i < args.length; i++) {
				String arg = args[i];
				int index = arg.indexOf('=');
				if (index == -1) {
					prtError("配置参数应该包含=号，例如threadNum=5");
				}
				String key = arg.substring(0, index);
				String value = arg.substring(index + 1, arg.length());

				if (!isValidKey(key)) {
					prtError("无效的配置名项:" + key + "，有效的配置项为" + validKeysString);
				}

				if (key.equalsIgnoreCase("threadNum")) {
					setThreadNum = true;
					try {
						config.setThreadNum(Integer.parseInt(value));
					} catch (NumberFormatException e) {
						prtError("下载线程数必须为数字");
					}
				}

				if (key.equalsIgnoreCase("sid")) {
					setSid = true;
					config.setSid(value);
				}

				if (key.equalsIgnoreCase("location")) {
					setLocation = true;
					config.setLocation(value);
				}

				if (key.equalsIgnoreCase("proxyAddr")) {
					setProxyAddr = true;
					config.setProxyAddr(value);
				}

				if (key.equalsIgnoreCase("proxyPort")) {
					setProxyPort = true;
					if (!value.isEmpty()) {
						try {
							config.setProxyPort(Integer.parseInt(value));
						} catch (NumberFormatException e) {
							prtError("代理端口必须为数字");
						}
					}
				}

				DowninsConfig prev = Configure.get().getConfig();
				if (!setLocation)
					config.setLocation(prev.getLocation());
				if (!setProxyAddr)
					config.setProxyAddr(prev.getProxyAddr());
				if (!setProxyPort)
					config.setProxyPort(prev.getProxyPort());
				if (!setSid)
					config.setSid(prev.getSid());
				if (!setThreadNum)
					config.setThreadNum(prev.getThreadNum());

			}
			try {
				Configure.get().store(config);
			} catch (LogicException e) {
				prtError(e.getMessage());
			} catch (IOException e) {
				prtError("保存失败");
			}

		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					new SettingFrame();
				}
			});
		}
	}

	private static void prtError(String msg) {
		System.out.println(msg);
		System.exit(-1);
	}

}
