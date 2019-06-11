package me.qyh.downinsrun;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.DowninsConfig;

class SettingFrame extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final JTextField locationText;
	private final JTextField threadNumText;
	private final JTextField proxyPortText;
	private final JTextField proxyAddrText;
	private final JTextArea sidArea;

	public SettingFrame() {

		super();
		DowninsConfig config = Configure.get().getConfig();
		setResizable(false);
		setLayout(null);
		setSize(400, 650);
		setLocationRelativeTo(null);

		JLabel locationLabel = new JLabel("存储文件夹路径");
		locationLabel.setBounds(10, 10, 400, 30);
		add(locationLabel);

		locationText = new JTextField();
		locationText.setText(config.getLocation());
		locationText.setBounds(10, 50, 300, 30);
		add(locationText);

		JButton locationBtn = new JButton("选择");
		locationBtn.setBounds(320, 50, 60, 30);
		add(locationBtn);
		locationBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						JFileChooser jfc = new JFileChooser(locationText.getText());
						jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
						int returnVal = jfc.showOpenDialog(SettingFrame.this);
						if (JFileChooser.APPROVE_OPTION == returnVal) {
							File file = jfc.getSelectedFile();
							locationText.setText(file.getAbsolutePath());
						}
					}
				});
			}
		});

		JLabel threadNumLabel = new JLabel("下载线程数");
		threadNumLabel.setBounds(10, 90, 100, 30);
		add(threadNumLabel);
		threadNumText = new JTextField(String.valueOf(config.getThreadNum()));
		threadNumText.setBounds(10, 130, 370, 30);
		add(threadNumText);

		JLabel proxyAddrLabel = new JLabel("代理地址");
		proxyAddrLabel.setBounds(10, 170, 300, 30);
		add(proxyAddrLabel);

		String proxyAddr = config.getProxyAddr() == null ? "" : config.getProxyAddr();
		proxyAddrText = new JTextField(proxyAddr);
		proxyAddrText.setBounds(10, 210, 370, 30);
		add(proxyAddrText);

		JLabel proxyPortLabel = new JLabel("代理端口");
		proxyPortLabel.setBounds(10, 250, 300, 30);
		add(proxyPortLabel);

		String proxyPort = config.getProxyPort() == null ? "" : String.valueOf(config.getProxyPort());
		proxyPortText = new JTextField(proxyPort);
		proxyPortText.setBounds(10, 290, 370, 30);
		add(proxyPortText);

		JLabel sidLabel = new JLabel("sessionid");
		sidLabel.setBounds(10, 330, 300, 30);
		add(sidLabel);
		String sid = config.getSid() == null ? "" : config.getSid();
		sidArea = new JTextArea(sid);
		sidArea.setBounds(10, 370, 370, 180);
		add(sidArea);

		JButton saveBtn = new JButton("保存");
		saveBtn.setBounds(280, 570, 100, 30);
		add(saveBtn);

		saveBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						DowninsConfig config = new DowninsConfig();
						config.setLocation(locationText.getText());
						try {
							config.setThreadNum(Integer.parseInt(threadNumText.getText()));
						} catch (NumberFormatException e) {
							JOptionPane.showMessageDialog(null, "下载线程必须为一个数字");
							return;
						}
						config.setProxyAddr(proxyAddrText.getText());
						if (!proxyPortText.getText().isEmpty()) {
							try {
								config.setProxyPort(Integer.parseInt(proxyPortText.getText()));
							} catch (NumberFormatException e) {
								JOptionPane.showMessageDialog(null, "代理端口必须为一个数字");
								return;
							}
						}
						config.setSid(sidArea.getText());
						try {
							Configure.get().store(config);
							JOptionPane.showMessageDialog(null, "保存成功");
						} catch (LogicException e) {
							JOptionPane.showMessageDialog(null, e.getMessage());
						} catch (IOException e) {
							JOptionPane.showMessageDialog(null, "保存失败");
						}
					}
				});
			}
		});

		setVisible(true);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

}