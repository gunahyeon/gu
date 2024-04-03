package com.dev.gu;

import com.dev.gu.dto.ApiResponse;
import com.dev.gu.dto.ApiResponseCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j(topic = "thumbnailController")
@RequestMapping("/api/v1")
public class ThumbnailController {
    final ThumbnailService thumbnailService;

    @Operation(summary = "썸네일 변환")
    @PostMapping(value = "/generate-thumbnails", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ApiResponse<Void>> uploadAndConvert(
            @RequestPart(value = "file") MultipartFile file
    ) {
        thumbnailService.generateThumbnails(file);
        return ApiResponse.toResponseEntity(ApiResponseCode.RESPONSE_OK);
    }

    @Operation(summary = "변환 작업 진행률 확인")
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<String>> getProgress() {
        return ApiResponse.toResponseEntity(
                ApiResponseCode.RESPONSE_OK,
                thumbnailService.getProgress()
        );
    }
}
