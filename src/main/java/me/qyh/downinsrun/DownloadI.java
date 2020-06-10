package me.qyh.downinsrun;

import java.nio.file.Path;

import me.qyh.downinsrun.parser.Https.DownloadProgressNotify;

/**
 * 下载某一个IGTV
 * 
 * @author wwwqyhme
 *
 */
class DownloadI extends DownloadP {

	private final PercentWrapper wrapper = new PercentWrapper(0);

	DownloadI(String code, Path dir) throws Exception {
		super(code, dir);

		super.setNotify((dest, percent) -> {
			if (percent > wrapper.percent) {
				wrapper.percent = percent;
				System.out.println(percent + "%");
			}
		});
	}

	private final class PercentWrapper {
		private double percent;

		private PercentWrapper(double percent) {
			super();
			this.percent = percent;
		}

	}

}
