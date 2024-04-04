package com.dev.gu;

import com.dev.gu.dto.ApiResponse;
import com.dev.gu.dto.ApiResponseCode;
import com.dev.gu.exception.BadRequestException;
import com.dev.gu.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j(topic = "thumbnailController")
@RequestMapping("/api/v1")
public class ThumbnailController {
    final ThumbnailService thumbnailService;

    @Operation(summary = "썸네일 변환", description = "taskId 로 변환 작업 진행률 상태 확인 가능")
    @PostMapping(value = "/generate-thumbnails/{taskId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ApiResponse<String>> uploadAndConvert(
            @PathVariable String taskId,
            @RequestPart(value = "file") MultipartFile file
    ) {
        try {
            CompletableFuture<String> future = thumbnailService.generateThumbnails(taskId, file);
            String result = future.get();
            return ApiResponse.toResponseEntity(ApiResponseCode.RESPONSE_OK, result);
        } catch (ExecutionException | InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new BadRequestException(ErrorCode.INVALID_FILE);
        }
    }

    @Operation(summary = "변환 작업 진행률 확인")
    @GetMapping("/progress/{taskId}")
    public ResponseEntity<ApiResponse<String>> getProgress(
            @PathVariable String taskId
    ) {
        return ApiResponse.toResponseEntity(
                ApiResponseCode.RESPONSE_OK,
                thumbnailService.getProgress(taskId)
        );
    }
}
