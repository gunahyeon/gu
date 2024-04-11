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
@RedisHash(value = "task")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Task {

    @Id
    String taskId;
    @Indexed
    Boolean status;
    @Indexed
    String roomId;
}
