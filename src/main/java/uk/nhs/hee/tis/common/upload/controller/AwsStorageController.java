package uk.nhs.hee.tis.common.upload.controller;

import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

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
