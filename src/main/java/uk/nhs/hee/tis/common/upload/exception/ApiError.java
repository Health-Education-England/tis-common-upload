package uk.nhs.hee.tis.common.upload.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class ApiError {

  private HttpStatus status;
  private String message;
  private String debugMessage;
}
