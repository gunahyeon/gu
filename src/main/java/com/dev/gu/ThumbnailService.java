package com.dev.gu;

import com.dev.gu.exception.BadRequestException;
import com.dev.gu.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ThumbnailService {
    final String OS = System.getProperty("os.name").toLowerCase();
    public boolean IS_MAC = (OS.contains("mac"));
    public boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);
    public boolean IS_WINDOWS = OS.contains("windows");    
    
    @Value("${app-config.file-upload.base-path}")
    String basePath;

    @Value("${app-config.file-upload.thumbnail-path}")
    String thumbnailPath;

    final RedisTemplate<String, String> redisTemplate;

    int completedImages = 0;

    /**
     * Redis에서 진행률 가져오기
     */
    public String getProgress(String taskId) {
        return String.valueOf(
                redisTemplate.opsForValue().get("thumbnail: " + taskId)
        );
    }

    /**
     * 썸네일 만들기 및 진행률 저장
     */
    @Async
    public CompletableFuture<String> generateThumbnails(String inputTaskId, MultipartFile file) {
        String zipFileFolderName = FilenameUtils.removeExtension(file.getOriginalFilename()) + RandomStringUtils.randomNumeric(6);
        Path unzipFolderPath = Paths.get(basePath + thumbnailPath + File.separator + zipFileFolderName);

        List<String> filePaths = new ArrayList<>();
        List<String> imageExtensions = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");

        /* 초기화 */
        int totalImages = 0;
        completedImages = 0;
        int progress = 0;
        String taskId = inputTaskId;
//        String taskId = file.getOriginalFilename() + "_" + inputTaskId;

        try (ZipInputStream zipIn = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry = zipIn.getNextEntry();

            /* 압축 파일 내의 각 항목에 대해 반복하여 파일의 총 개수를 계산 */
            while (entry != null) {
                String fileExt = entry.getName().toLowerCase();

                if (imageExtensions.stream().anyMatch(fileExt::endsWith)) {
                    // 이미지 파일인 경우에만 개수를 증가시킴
                    ++totalImages;
                }

                String filePath = basePath + thumbnailPath + File.separator +
                        zipFileFolderName + File.separator + entry.getName();

                if (!Files.exists(unzipFolderPath)) {
                    Files.createDirectories(unzipFolderPath);
                }

                /* 압축해제 */
                unzipImage(zipIn, filePath);

                filePaths.add(filePath);
                entry = zipIn.getNextEntry();
            }

            /* 초기화 */
            redisTemplate.opsForValue().set(
                    "thumbnail: " + taskId,
                    totalImages + "개 이미지가 있음, " + completedImages + "개 변환된 상태, " + progress + "% 출력",
                    Duration.ofHours(12)
            );

            redisTemplate.opsForValue().set(
                    "thumbnail_totalImages: " + taskId,
                    String.valueOf(totalImages),
                    Duration.ofHours(12)
            );

            redisTemplate.opsForValue().set(
                    "thumbnail_completedImages: " + taskId,
                    String.valueOf(completedImages),
                    Duration.ofHours(12)
            );

            /* 변환 */
            extractFile(filePaths, taskId);

            /* 변환 완료 된 폴더 삭제 */
            File unzipFolder = new File(String.valueOf(unzipFolderPath));
            FileUtils.cleanDirectory(unzipFolder);
            FileUtils.delete(unzipFolder);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return CompletableFuture.completedFuture(taskId);
    }

    /**
     * 압축해제
     */
    private void unzipImage(ZipInputStream zipIn, String filePath) {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[4096];
            int read = 0;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 압축 처리된 폴더 내 이미지 파일 변환
     */
    private void extractFile(List<String> filePaths, String taskId) {
        for (String filePath : filePaths) {
            File file = new File(filePath);
            ProcessBuilder builder = new ProcessBuilder();
            Path thumbnailFolderPath = Path.of(basePath, thumbnailPath);
            List<Process> processes = new ArrayList<>();

            try {
                if (!Files.exists(thumbnailFolderPath)) {
                    Files.createDirectories(thumbnailFolderPath);
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            String outputFile = thumbnailFolderPath.resolve(
                    FilenameUtils.removeExtension(thumbnailFolderPath + File.separator + file.getName()) + ".webp"
            ).toAbsolutePath().toString();

            String[] defaultCmd = new String[]{
                    getCWebpPath(),
                    "-mt",
                    "-q", "50", // 압축률 50%
            };
            String[] defaultGifCmd = new String[]{
                    getCWebpPath(false),
                    "-mt",
                    "-lossy",
            };

            boolean isGif = file.getName().endsWith(".gif");

            List<String> cmd = new ArrayList<>(List.of(isGif ? defaultGifCmd : defaultCmd));

            builder.directory(file.getParentFile());
            cmd.add(file.getName());
            cmd.add("-o");
            cmd.add(outputFile);
            builder.command(cmd);

            try {
                log.info("BUILDER: {}", builder.directory());
                log.info("COMMAND: {}", builder.command());
                Process process = builder.start();
                processes.add(process);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new BadRequestException(ErrorCode.INVALID_FILE);
            }

            for (Process prc : processes) {
                try {
                    prc.waitFor();
                    prc.destroy();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            /* 변환 진행률을 Redis에 저장. */
            int totalImages = Integer.parseInt(String.valueOf(
                    redisTemplate.opsForValue().get("thumbnail_totalImages: " + taskId)
            ));

            completedImages = Integer.parseInt(String.valueOf(
                    redisTemplate.opsForValue().get("thumbnail_completedImages: " + taskId)
            ));

            completedImages++;

            redisTemplate.opsForValue().set(
                    "thumbnail_completedImages: " + taskId,
                    String.valueOf(completedImages),
                    Duration.ofHours(12)
            );

            int progress = (int)(((double)completedImages / totalImages) * 100);

            redisTemplate.opsForValue().set(
                    "thumbnail: " + taskId,
                    totalImages + "개 이미지가 있음, " + completedImages + "개 변환된 상태, " + progress + "% 출력",
                    Duration.ofHours(12)
            );

            /* delay */
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
    }

    /**
     * Google WebP 변환 도구를 사용하여 이미지 변환
     */
    public String getCWebpPath(boolean isWebp) {
        Path binaryPaths = Paths.get(
                basePath,
                "binary",
                IS_UNIX ? "libwebp-1.3.2-linux-x86-64" :
                        IS_MAC ? "libwebp-1.3.2-mac-arm64" :
                                IS_WINDOWS ? "libwebp-1.3.2-windows-x64" : "",
                "bin",
                isWebp ? "cwebp" : "gif2webp");

        if (IS_UNIX || IS_MAC || IS_WINDOWS) {
            return binaryPaths.toAbsolutePath().toString();
        } else {
            return null;
        }
    }

    public String getCWebpPath() {
        return getCWebpPath(true);
    }    
}
