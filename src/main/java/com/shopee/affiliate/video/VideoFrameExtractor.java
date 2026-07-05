package com.shopee.affiliate.video;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class xử lý trích xuất các khung ảnh tĩnh từ video sản phẩm.
 */
public class VideoFrameExtractor {

    /**
     * Trích xuất các khung hình chính từ file video và lưu vào thư mục đầu ra.
     *
     * @param videoFile  File video đầu vào (.mp4)
     * @param outputDir  Thư mục đầu ra để lưu ảnh
     * @param numFrames  Số lượng khung hình cần trích xuất
     * @return Danh sách các file ảnh đã trích xuất
     * @throws Exception Nếu xảy ra lỗi trong quá trình xử lý video
     */
    public static List<File> extractFrames(File videoFile, File outputDir, int numFrames) throws Exception {
        List<File> extractedFiles = new ArrayList<>();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Không thể tạo thư mục lưu ảnh: " + outputDir.getAbsolutePath());
        }

        System.out.println("Bắt đầu trích xuất " + numFrames + " khung hình từ video: " + videoFile.getName());

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
            grabber.start();
            
            long durationUs = grabber.getLengthInTime(); // Độ dài video tính bằng microseconds
            if (durationUs <= 0) {
                // Thử ước tính dựa trên frame rate nếu không lấy được độ dài trực tiếp
                int totalFrames = grabber.getLengthInFrames();
                double frameRate = grabber.getFrameRate();
                if (totalFrames > 0 && frameRate > 0) {
                    durationUs = (long) ((totalFrames / frameRate) * 1_000_000);
                }
            }

            if (durationUs <= 0) {
                throw new IOException("Không thể xác định độ dài của video: " + videoFile.getAbsolutePath());
            }

            Java2DFrameConverter converter = new Java2DFrameConverter();
            
            // Chia đều khoảng thời gian video để cắt ảnh (ví dụ 4 ảnh thì cắt tại 20%, 40%, 60%, 80%)
            double step = 1.0 / (numFrames + 1);
            for (int i = 1; i <= numFrames; i++) {
                double ratio = step * i;
                long targetTimeUs = (long) (durationUs * ratio);
                
                // Di chuyển con trỏ phát đến mốc thời gian mục tiêu
                grabber.setTimestamp(targetTimeUs);
                
                Frame frame = grabber.grabImage();
                if (frame != null) {
                    BufferedImage bi = converter.convert(frame);
                    if (bi != null) {
                        File outFile = new File(outputDir, "frame_" + i + ".png");
                        ImageIO.write(bi, "png", outFile);
                        extractedFiles.add(outFile);
                        System.out.println("  -> Đã lưu khung hình " + i + " tại " + (targetTimeUs / 1_000_000) + "s: " + outFile.getName());
                    }
                }
            }
            grabber.stop();
        }
        
        System.out.println("Hoàn thành trích xuất " + extractedFiles.size() + " ảnh từ video " + videoFile.getName());
        return extractedFiles;
    }
}
