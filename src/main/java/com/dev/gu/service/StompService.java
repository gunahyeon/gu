package com.dev.gu.service;

import com.dev.gu.dto.Progress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StompService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendProgressList(String roomId, List<Progress> progressList) {
        messagingTemplate.convertAndSend("/sub/message/" + roomId, progressList);
    }
}
