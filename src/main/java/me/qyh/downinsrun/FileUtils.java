package me.qyh.downinsrun;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {

	private static final char SPLITER = '/';
	private static final char SPLITER2 = '\\';

	public static String cleanPath(String path) {
		if (path == null || path.trim().isEmpty()) {
			return "";
		}
		char[] chars = path.toCharArray();
		char prev = chars[0];
		char last = SPLITER;
		if (chars.length == 1) {
			if (prev == SPLITER || prev == SPLITER2) {
				return "";
			}
			return Character.toString(prev);
		}
		if (prev == SPLITER2) {
			prev = SPLITER;
		}
		StringBuilder sb = new StringBuilder();
		if (prev != SPLITER) {
			sb.append(prev);
		}
		for (int i = 1; i < chars.length; i++) {
			char ch = chars[i];
			if (ch == SPLITER || ch == SPLITER2) {
				if (prev == SPLITER) {
					continue;
				}
				prev = SPLITER;
				if (i < chars.length - 1) {
					sb.append(SPLITER);
					last = SPLITER;
				}
			} else {
				prev = ch;
				sb.append(ch);
				last = ch;
			}
		}
		if (last == SPLITER) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public static void deleteDir(Path dir) {
		if (dir != null && Files.exists(dir)) {
			try {
				Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						try {
							Files.delete(file);
						} catch (Exception e) {
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						try {
							Files.delete(dir);
						} catch (Exception e) {
						}
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (Exception e) {
			}
		}
	}
}
