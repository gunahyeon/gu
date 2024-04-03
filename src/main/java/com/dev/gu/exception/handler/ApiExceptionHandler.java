package com.dev.gu.exception.handler;

import com.dev.gu.exception.BadRequestException;
import com.dev.gu.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


@Slf4j
@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(BadRequestException e) {
        log.error(e.getMessage(), e);
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }
}
