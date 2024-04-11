package com.dev.gu.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@RedisHash(value = "progress")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Progress {

    @Id
    String folderName;
    int totalImages;
    int completedImages;
    int progress;
    int convertingImages;
    @Indexed
    String taskId;
}
