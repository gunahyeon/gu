package com.dev.gu.service;

import com.dev.gu.dto.Progress;
import com.dev.gu.dto.Task;
import com.dev.gu.exception.BadRequestException;
import com.dev.gu.exception.ErrorCode;
import com.dev.gu.repository.ProgressRedisRepository;
import com.dev.gu.repository.TaskRedisRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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

    private final StompService stompService;

    private final TaskRedisRepository taskRedisRepository;
    private final ProgressRedisRepository progressRedisRepository;

    /**
     * 폴더 내 파일 리스트 가져오기
     */
    public List<Map<String, Object>> getImageList(String taskId) {
        Task task = taskRedisRepository.findById(taskId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.TASK_IS_NOT_EXIST));

        if (task.getStatus()) {
            List<Progress> folderNames = progressRedisRepository.findAllByTaskId(taskId);

            /* taskId 내 폴더 리스트 가져오기 */
            List<Map<String, Object>> list = new ArrayList<>();
            List<String> imageList = new ArrayList<>();

            for (Progress progress : folderNames) {
                Map<String, Object> map = new HashMap<>();
                map.put("folderName", progress.getFolderName());
                
                Path folderPath = Paths.get(basePath + thumbnailPath + File.separator + progress.getFolderName());

                File folder = folderPath.toFile();
                File[] files = folder.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            imageList.add(file.getName());
                        }
                    }
                }

                /* 이름순으로 정렬 */
                imageList.sort(Comparator.comparing(String::toString));

                map.put("imageList", imageList);
                list.add(map);
            }

            return list;
        } else {
            throw new BadRequestException(ErrorCode.TASK_IS_NOT_COMPlETE);
        }
    }

    /**
     * 썸네일 만들기 및 진행률 저장
     */
    @Async
    @Transactional
    public CompletableFuture<String> generateThumbnails(String taskId, String roomId, MultipartFile[] files) {
        taskRedisRepository.save(new Task(taskId, false, roomId));

        List<String> folderNames = new ArrayList<>();
        List<Progress> progressList = new ArrayList<>();
        Map<String, List<String>> map = new HashMap<>();

        for (MultipartFile file : files) {
            String zipFileFolderName = FilenameUtils.removeExtension(file.getOriginalFilename()) + RandomStringUtils.randomNumeric(6);
            Path unzipFolderPath = Paths.get(basePath + thumbnailPath + File.separator + zipFileFolderName + "_unzip");

            List<String> filePaths = new ArrayList<>();
            List<String> imageExtensions = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");

            /* 초기화 */
            int totalImages = 0;
            int completedImages = 0;
            int progress = 0;
            String folder = zipFileFolderName;

            try (ZipInputStream zipIn = new ZipInputStream(file.getInputStream())) {
                ZipEntry entry = zipIn.getNextEntry();

                /* 압축 파일 내의 각 항목에 대해 반복하여 파일의 총 개수를 계산 */
                while (entry != null) {
                    String fileExt = entry.getName().toLowerCase();

                    if (imageExtensions.stream().anyMatch(fileExt::endsWith)) {
                        /* 이미지 파일인 경우에만 개수를 증가시킴 */
                        ++totalImages;
                    }

                    String filePath = basePath + thumbnailPath + File.separator +
                            zipFileFolderName + "_unzip" + File.separator + entry.getName();

                    if (!Files.exists(unzipFolderPath)) {
                        Files.createDirectories(unzipFolderPath);
                    }

                    /* 압축해제 */
                    unzipImage(zipIn, filePath);

                    filePaths.add(filePath);
                    entry = zipIn.getNextEntry();
                }

                /* 초기화 */
                Progress prgrss = new Progress(folder, totalImages, completedImages, progress, totalImages != 0 ? totalImages - completedImages : 0, taskId);
                progressRedisRepository.save(prgrss);
                progressList.add(prgrss);
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            folderNames.add(folder);
            map.put(folder, filePaths);
        }

        /* 초기화된 진행률 리스트 발송 */
        stompService.sendProgressList(roomId, progressList);

        for (String folder : folderNames) {
            Path unzipFolderPath = Paths.get(basePath + thumbnailPath + File.separator + folder + "_unzip");

            /* 변환 */
            extractFile(map.get(folder), folder, progressList, roomId);
            /* 변환 완료 된 폴더 삭제 */
            File unzipFolder = new File(String.valueOf(unzipFolderPath));

            try {
                FileUtils.cleanDirectory(unzipFolder);
                FileUtils.delete(unzipFolder);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }

        /* taskId 상태 업데이트 */
        Task task = taskRedisRepository.findById(taskId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.TASK_IS_NOT_EXIST));

        task.setStatus(true);
        taskRedisRepository.save(task);

        log.info("서버 파일 처리 완료");

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
    private void extractFile(List<String> filePaths, String folder, List<Progress> progressList, String roomId) {
        for (String filePath : filePaths) {
            File file = new File(filePath);
            ProcessBuilder builder = new ProcessBuilder();
            Path thumbnailFolderPath = Path.of(basePath, thumbnailPath, folder);
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

            /* 1개의 태스크에 대한 진행률 리스트 */
            List<Progress> progresses = progressList.stream().map(prss -> {
                            if (prss.getFolderName().equals(folder)) {
                                int totalImages = prss.getTotalImages();

                                int completedImages = prss.getCompletedImages();
                                prss.setCompletedImages(++completedImages);

                                int progress = (int)(((double)completedImages / totalImages) * 100);
                                prss.setProgress(progress);

                                prss.setConvertingImages(totalImages - completedImages);
                            }
                            return prss;
                        }).toList();

            /* 변환 진행률을 Redis에 저장. */
            progressRedisRepository.saveAll(progresses);

            /* 진행률 리스트 메세지 발송 */
            stompService.sendProgressList(roomId, progresses);
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

    /**
     * 폴더 내 파일 가져오기
     */
    public byte[] getImage(String folderName, String fileName) {
        Path filePath = Paths.get(
                basePath + thumbnailPath + File.separator + folderName + File.separator + fileName
        );

        try {
            InputStream in = new FileInputStream(filePath.toString());
            return IOUtils.toByteArray(in);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return null;
    }

    /**
     * 방 ID 만들기
     */
    public String generateRoomId() {
        String roomId = UUID.randomUUID().toString();
        
        /* 생성된 room 아이디로 채널 생성 */
        ChannelTopic channelTopic = new ChannelTopic(roomId);

        return channelTopic.getTopic();
    }
}
