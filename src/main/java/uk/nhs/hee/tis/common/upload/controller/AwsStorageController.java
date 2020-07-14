package uk.nhs.hee.tis.common.upload.controller;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.util.Objects;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.common.upload.dto.DownloadDto;
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;
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

  @GetMapping
  public ResponseEntity downloadFile(@RequestParam("bucketName") final String bucketName,
      @RequestParam("folderPath") final String folderPath,
      @RequestParam("fileName") final String fileName) {

    if (Objects.nonNull(bucketName) && Objects.nonNull(folderPath) && Objects.nonNull(fileName)) {

      log.info("Request receive to download file: {} from bucket: {}", fileName, bucketName);
      final var downloadDto = DownloadDto.builder().bucketName(bucketName)
          .folderPath(folderPath).fileName(fileName).build();
      final var response = awsStorageService.download(downloadDto);
      final ByteArrayResource resource = new ByteArrayResource(response);
      return ResponseEntity
          .ok()
          .contentLength(response.length)
          .header(CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
          .header(CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
          .body(resource);
    } else {
      throw new AwsStorageException(
          "Bucket Name, File Name and Folder Path all parameters required to serve download");
    }
  }
}
