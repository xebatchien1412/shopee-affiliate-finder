package com.shopee.affiliate.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.shopee.affiliate.browser.ShopeeAutomation;
import com.shopee.affiliate.browser.ShopeeAutomation.ShopeeProduct;
import com.shopee.affiliate.config.AppConfig;
import com.shopee.affiliate.matcher.GeminiVlmComparator;
import com.shopee.affiliate.matcher.GeminiVlmComparator.MatchResult;
import com.shopee.affiliate.matcher.TextMatcher;
import com.shopee.affiliate.model.MetadataManager;
import com.shopee.affiliate.model.ProductMetadata;
import com.shopee.affiliate.video.VideoFrameExtractor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Giao diện đồ họa (GUI) cho ứng dụng Shopee Affiliate Finder sử dụng FlatLaf Dark Mode.
 */
public class AppFrame extends JFrame {

    private JTextField txtInputDir;
    private JTextField txtApiKey;
    private JSpinner spinMaxLinks;
    private JSpinner spinFrames;
    private JProgressBar progressBar;
    private JTextArea txtLog;
    private JButton btnRun;
    private JButton btnBrowse;
    private JCheckBox chkCdp;
    private JComboBox<String> cmbModel;
    
    private SwingWorker<Void, String> worker;

    public AppFrame() {
        setTitle("Shopee Affiliate Link Finder");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null); // Canh giữa màn hình
        initComponents();
        setupLoggingRedirect();
    }

    private void initComponents() {
        // Cấu hình panel chính với khoảng đệm lớn
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(mainPanel);

        // --- Panel Tiêu đề (Header) ---
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel lblTitle = new JLabel("SHOPEE AFFILIATE LINK FINDER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(255, 87, 34)); // Màu cam Shopee
        headerPanel.add(lblTitle);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- Panel Điều khiển (Config & Controls) ---
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình hệ thống"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        // Ô chọn thư mục đầu vào
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        configPanel.add(new JLabel("Thư mục sản phẩm:"), gbc);

        txtInputDir = new JTextField(AppConfig.getInputDir());
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        configPanel.add(txtInputDir, gbc);

        btnBrowse = new JButton("Chọn thư mục...");
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.0;
        configPanel.add(btnBrowse, gbc);

        // Ô nhập Gemini API Key
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        configPanel.add(new JLabel("Gemini API Key:"), gbc);

        txtApiKey = new JTextField(AppConfig.getGeminiApiKey());
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        configPanel.add(txtApiKey, gbc);
        gbc.gridwidth = 1; // reset gridwidth

        // Thiết lập nâng cao (Số link & Số khung ảnh)
        JPanel advancedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        advancedPanel.add(new JLabel("Số link lấy tối đa:"));
        spinMaxLinks = new JSpinner(new SpinnerNumberModel(AppConfig.getMaxAffiliateLinks(), 1, 20, 1));
        advancedPanel.add(spinMaxLinks);

        advancedPanel.add(new JLabel("Số ảnh cắt từ video:"));
        spinFrames = new JSpinner(new SpinnerNumberModel(AppConfig.getExtractFramesCount(), 1, 10, 1));
        advancedPanel.add(spinFrames);

        advancedPanel.add(new JLabel("Model AI:"));
        String[] models = {"gemini-2.5-flash", "gemini-1.5-pro", "gemini-3.1-flash-lite"};
        cmbModel = new JComboBox<>(models);
        cmbModel.setSelectedItem(AppConfig.getGeminiModel());
        advancedPanel.add(cmbModel);

        chkCdp = new JCheckBox("Kết nối Chrome 9222 (CDP Mode)", AppConfig.isCdp());
        chkCdp.setToolTipText("Bật để kết nối trực tiếp vào cửa sổ trình duyệt Chrome thật đang mở sẵn qua file bat");
        advancedPanel.add(chkCdp);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0;
        gbc.gridwidth = 3;
        configPanel.add(advancedPanel, gbc);
        gbc.gridwidth = 1; // reset

        // --- Panel Tiến trình và Nút chạy ---
        JPanel actionPanel = new JPanel(new BorderLayout(10, 10));
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        actionPanel.add(progressBar, BorderLayout.CENTER);

        btnRun = new JButton("Bắt đầu xử lý");
        btnRun.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRun.setBackground(new Color(255, 87, 34));
        btnRun.setForeground(Color.WHITE);
        actionPanel.add(btnRun, BorderLayout.EAST);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        configPanel.add(actionPanel, gbc);

        mainPanel.add(configPanel, BorderLayout.CENTER);

        // --- Panel Log hiển thị thông tin thời gian thực ---
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Nhật ký xử lý (Console Log)"));
        
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(30, 30, 30));
        txtLog.setForeground(new Color(220, 220, 220));
        
        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setPreferredSize(new Dimension(800, 220));
        logPanel.add(scrollPane, BorderLayout.CENTER);
        
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        // --- Sự kiện cho các nút ---
        btnBrowse.addActionListener(e -> chooseFolder());
        btnRun.addActionListener(e -> toggleExecution());
    }

    /**
     * Mở hộp thoại chọn thư mục đầu vào.
     */
    private void chooseFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(txtInputDir.getText()));
        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            txtInputDir.setText(file.getAbsolutePath());
            saveCurrentConfig(); // Lưu trạng thái thư mục ngay khi chọn xong
        }
    }

    /**
     * Lưu cấu hình hiện tại vào tệp config.properties cục bộ.
     */
    private void saveCurrentConfig() {
        String inputDir = txtInputDir.getText().trim();
        String apiKey = txtApiKey.getText().trim();
        String model = (String) cmbModel.getSelectedItem();
        int maxLinks = (Integer) spinMaxLinks.getValue();
        int extractFrames = (Integer) spinFrames.getValue();
        boolean isCdp = chkCdp.isSelected();
        
        AppConfig.saveProperties(inputDir, apiKey, model, maxLinks, extractFrames, isCdp);
    }

    /**
     * Chuyển đổi trạng thái Bắt đầu / Dừng chạy luồng tự động hóa.
     */
    private void toggleExecution() {
        if (worker != null && !worker.isDone()) {
            // Yêu cầu dừng chạy
            worker.cancel(true);
            btnRun.setText("Đang dừng...");
            btnRun.setEnabled(false);
            return;
        }

        // Kiểm tra dữ liệu đầu vào trước khi chạy
        String inputDirStr = txtInputDir.getText().trim();
        File inputDir = new File(inputDirStr);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Thư mục sản phẩm không tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String apiKey = txtApiKey.getText().trim();
        if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY")) {
            int confirm = JOptionPane.showConfirmDialog(this, 
                    "Bạn chưa nhập Gemini API Key hoặc đang dùng key mặc định.\nNếu tiếp tục, hệ thống sẽ bỏ qua bước đối sánh ảnh bằng AI và không thể tự động nhận biết sản phẩm trùng khớp.\nBạn có muốn tiếp tục không?", 
                    "Cảnh báo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Cập nhật lại thuộc tính hệ thống tạm thời để các luồng sau sử dụng
        System.setProperty("gui.input.dir", inputDirStr);
        System.setProperty("gui.gemini.key", apiKey);
        System.setProperty("gui.max.links", spinMaxLinks.getValue().toString());
        System.setProperty("gui.extract.frames", spinFrames.getValue().toString());
        System.setProperty("gui.gemini.model", cmbModel.getSelectedItem().toString());
        System.setProperty("gui.browser.cdp", String.valueOf(chkCdp.isSelected()));

        // Lưu cấu hình hiện tại vào file để lần sau mở lên giữ nguyên trạng thái
        saveCurrentConfig();

        txtLog.setText(""); // Xóa sạch log cũ
        progressBar.setValue(0);
        progressBar.setString("Đang bắt đầu...");
        btnRun.setText("Dừng chạy");
        btnRun.setBackground(Color.RED);
        setComponentsEnabled(false);

        // Khởi chạy SwingWorker chạy ngầm
        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                runAutomationLogic();
                return null;
            }

            @Override
            protected void done() {
                setComponentsEnabled(true);
                btnRun.setText("Bắt đầu xử lý");
                btnRun.setBackground(new Color(255, 87, 34));
                btnRun.setEnabled(true);
                progressBar.setValue(100);
                if (isCancelled()) {
                    progressBar.setString("Đã dừng bởi người dùng.");
                    System.out.println("\n>>> TIẾN TRÌNH ĐÃ BỊ DỪNG BỞI NGƯỜI DÙNG. <<<");
                } else {
                    progressBar.setString("Hoàn thành toàn bộ!");
                    System.out.println("\n>>> HOÀN THÀNH TOÀN BỘ CÔNG VIỆC! <<<");
                }
            }
        };

        worker.execute();
    }

    private void setComponentsEnabled(boolean enabled) {
        txtInputDir.setEnabled(enabled);
        txtApiKey.setEnabled(enabled);
        spinMaxLinks.setEnabled(enabled);
        spinFrames.setEnabled(enabled);
        btnBrowse.setEnabled(enabled);
        chkCdp.setEnabled(enabled);
        cmbModel.setEnabled(enabled);
    }

    /**
     * Quy trình xử lý tự động hóa được thực thi trên background thread.
     */
    private void runAutomationLogic() throws Exception {
        String inputDirStr = System.getProperty("gui.input.dir");
        String apiKey = System.getProperty("gui.gemini.key");
        int maxLinks = Integer.parseInt(System.getProperty("gui.max.links"));
        int frameCount = Integer.parseInt(System.getProperty("gui.extract.frames"));

        File inputDir = new File(inputDirStr);
        File[] productFolders = inputDir.listFiles(File::isDirectory);
        
        if (productFolders == null || productFolders.length == 0) {
            System.out.println("Không tìm thấy thư mục sản phẩm con nào.");
            return;
        }

        int totalFolders = productFolders.length;
        System.out.println("Bắt đầu xử lý " + totalFolders + " thư mục sản phẩm...");

        // Khởi tạo Automation và VLM Comparator với API Key lấy từ GUI
        ShopeeAutomation automation = new ShopeeAutomation();
        GeminiVlmComparator vlmComparator = new GeminiVlmComparator(apiKey);

        File tempDir = new File("temp_frames");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        try {
            automation.init();
            automation.checkLoginAndNavigate();

            for (int idx = 0; idx < totalFolders; idx++) {
                if (worker.isCancelled()) {
                    break;
                }

                File folder = productFolders[idx];
                final int currentIdx = idx;
                
                // Cập nhật Progress Bar
                SwingUtilities.invokeLater(() -> {
                    int percent = (int) ((double) currentIdx / totalFolders * 100);
                    progressBar.setValue(percent);
                    progressBar.setString("Đang xử lý: " + folder.getName() + " (" + (currentIdx + 1) + "/" + totalFolders + ")");
                });

                System.out.println("\n------------------------------------------------------------");
                System.out.println("ĐANG XỬ LÝ SẢN PHẨM: " + folder.getName());

                ProductMetadata metadata;
                try {
                    metadata = MetadataManager.readMetadata(folder);
                } catch (IOException e) {
                    System.err.println("Lỗi đọc metadata: " + e.getMessage());
                    continue;
                }

                System.out.println("  -> Tên sản phẩm: " + metadata.getProductName());

                if (metadata.getAffiliateLinks().size() >= maxLinks) {
                    System.out.println("  -> Sản phẩm đã đủ số link affiliate yêu cầu. Bỏ qua.");
                    continue;
                }

                if (metadata.getVideoFile() == null) {
                    System.err.println("  -> Không có video MP4. Bỏ qua.");
                    continue;
                }

                // 1. Trích xuất video
                File productTempDir = new File(tempDir, folder.getName());
                List<File> extractedFrames = new ArrayList<>();
                try {
                    extractedFrames = VideoFrameExtractor.extractFrames(
                            metadata.getVideoFile(), productTempDir, frameCount
                    );
                } catch (Exception e) {
                    System.err.println("  -> Lỗi trích xuất video: " + e.getMessage());
                    cleanDirectory(productTempDir);
                    continue;
                }

                if (extractedFrames.isEmpty()) {
                    System.err.println("  -> Không trích xuất được ảnh video.");
                    cleanDirectory(productTempDir);
                    continue;
                }

                // 2. Tìm kiếm, đối sánh và lấy link trực tiếp theo từng trang (tránh lỗi stale element của Playwright)
                List<String> updatedLinks = automation.searchAndProcessAffiliateLinks(
                        metadata.getProductName(),
                        vlmComparator,
                        extractedFrames,
                        maxLinks,
                        metadata.getAffiliateLinks(),
                        () -> worker.isCancelled()
                );

                if (worker.isCancelled()) {
                    cleanDirectory(productTempDir);
                    break;
                }

                if (updatedLinks.size() > metadata.getAffiliateLinks().size()) {
                    metadata.setAffiliateLinks(updatedLinks);
                    try {
                        MetadataManager.writeMetadata(metadata);
                        System.out.println("  => Đã cập nhật file metadata.txt thành công!");
                    } catch (IOException e) {
                        System.err.println("  => Lỗi ghi file metadata.txt: " + e.getMessage());
                    }
                } else {
                    System.out.println("  => Không tìm thấy thêm sản phẩm trùng khớp trên Shopee hoặc không lấy được thêm link.");
                }

                // Luôn xóa các hình ảnh trích xuất ngay sau khi phân tích xong sản phẩm này
                cleanDirectory(productTempDir);
            }

        } finally {
            automation.close();
            cleanDirectory(tempDir); // Xóa sạch thư mục tạm
        }
    }

    /**
     * Định hướng luồng ghi System.out và System.err sang JTextArea để cập nhật log thời gian thực.
     */
    private void setupLoggingRedirect() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                updateLogTextArea(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                updateLogTextArea(new String(b, off, len, StandardCharsets.UTF_8));
            }
        };

        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private void updateLogTextArea(final String text) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(text);
            // Tự động cuộn xuống cuối cùng
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void cleanDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Lỗi xóa file tạm: " + e.getMessage());
        }
    }

    public static void launch() {
        // Cấu hình giao diện FlatLaf Dark Mode cho sang trọng, hiện đại
        try {
            FlatDarkLaf.setup();
        } catch (Exception ex) {
            System.err.println("Không thể thiết lập theme FlatLaf. Sử dụng theme mặc định.");
        }

        SwingUtilities.invokeLater(() -> {
            AppFrame frame = new AppFrame();
            frame.setVisible(true);
        });
    }
}
