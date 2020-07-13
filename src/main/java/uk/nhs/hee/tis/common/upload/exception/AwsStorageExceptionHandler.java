package uk.nhs.hee.tis.common.upload.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@ControllerAdvice
public class AwsStorageExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(AwsStorageException.class)
  protected ResponseEntity<Object> handleEntityNotFound(final AwsStorageException ex) {
    log.info("Handling exception from API");
    final var apiError = ApiError.builder()
        .status(HttpStatus.BAD_REQUEST)
        .message(ex.getMessage())
        .build();
    return buildResponseEntity(apiError);
  }

  private ResponseEntity<Object> buildResponseEntity(final ApiError apiError) {
    return new ResponseEntity<>(apiError, apiError.getStatus());
  }
}
