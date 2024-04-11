package com.dev.gu.controller;

import com.dev.gu.dto.ApiResponse;
import com.dev.gu.dto.ApiResponseCode;
import com.dev.gu.exception.BadRequestException;
import com.dev.gu.exception.ErrorCode;
import com.dev.gu.repository.TaskRedisRepository;
import com.dev.gu.service.ThumbnailService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j(topic = "thumbnailController")
@RequestMapping("/api/v1")
public class ThumbnailController {
    final ThumbnailService thumbnailService;
    final TaskRedisRepository taskRedisRepository;

    @Operation(summary = "썸네일 변환", description = "taskId 로 변환 작업 진행률 상태 확인 가능")
    @PostMapping(value = "/generate-thumbnails/{roomId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ApiResponse<String>> uploadAndConvert(
            @PathVariable String roomId,
            @RequestPart(value = "files") MultipartFile[] files
    ) {
        /* 태스크 아이디 생성 */
        String taskId = UUID.randomUUID().toString();

        /* 태스크 갯수 & 태스크 처리 상태 확인 (최대 5개) */
        if (taskRedisRepository.findAllByStatusAndRoomId(false, roomId).size() >= 5) {
            throw new BadRequestException(ErrorCode.EXCEED_MAX_UPLOAD_COUNT);
        }

        try {
            CompletableFuture<String> future = thumbnailService.generateThumbnails(taskId, roomId, files);
            log.info("서버 파일 처리 완료11");

            String result = future.get();
            log.info("서버 파일 처리 완료11111111111");
            return ApiResponse.toResponseEntity(ApiResponseCode.RESPONSE_OK, result);
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new BadRequestException(ErrorCode.INVALID_FILE);
        }
    }

    @Operation(summary = "태스크 내 [폴더-파일 리스트] 가져오기")
    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getImageList(
            @PathVariable String taskId
    ) {
        return ApiResponse.toResponseEntity(
                ApiResponseCode.RESPONSE_OK,
                thumbnailService.getImageList(taskId)
        );
    }

    @Operation(summary = "폴더 내 파일 가져오기")
    @GetMapping(value = "/image/{folderName}/{fileName}", produces = MediaType.IMAGE_JPEG_VALUE)
    public @ResponseBody byte[] getImage(
            @PathVariable String folderName,
            @PathVariable String fileName
    ) {

        return thumbnailService.getImage(folderName, fileName);
    }

    @Operation(summary = "채널 생성")
    @GetMapping
    public ResponseEntity<ApiResponse<String>> generateRoomId() {
        return ApiResponse.toResponseEntity(
                ApiResponseCode.RESPONSE_OK,
                thumbnailService.generateRoomId()
        );
    }
}
