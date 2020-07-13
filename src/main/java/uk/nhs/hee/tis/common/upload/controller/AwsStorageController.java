package uk.nhs.hee.tis.common.upload.controller;

import com.amazonaws.services.s3.model.PutObjectResult;
import java.io.IOException;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.nhs.hee.tis.common.upload.AwsStorageService;
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;

@Slf4j
@RestController
@RequestMapping("/api/storage")
public class AwsStorageController {

  @Autowired
  private AwsStorageService awsStorageService;

  @PostMapping
  public ResponseEntity uploadFile(@ModelAttribute @Valid FileUploadDto fileUpload) {
    log.info("Request receive to upload file: {}", fileUpload);
    final var response = awsStorageService.upload(fileUpload);
    return ResponseEntity.ok(response);
  }
}
