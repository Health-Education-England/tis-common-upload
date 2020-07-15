package uk.nhs.hee.tis.common.upload.controller;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.util.Objects;
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
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

@Slf4j
@RestController
@RequestMapping("/api/storage")
public class AwsStorageController {

  @Autowired
  private AwsStorageService awsStorageService;

  /**
   * API to upload file to s3.
   *
   * @param storageDto to upload
   * @return Response entity with status code 200
   */
  @PostMapping("/upload")
  public ResponseEntity uploadFile(@ModelAttribute StorageDto storageDto) {

    if (Objects.nonNull(storageDto.getBucketName()) && Objects.nonNull(storageDto.getFolderPath())
        && Objects.nonNull(storageDto.getFile())) {
      log.info("Request receive to upload file: {}", storageDto);
      final var response = awsStorageService.upload(storageDto);
      return ResponseEntity.ok(response);
    } else {
      throw new AwsStorageException(
          "Bucket Name, File and Folder Path all parameters required to serve download");
    }
  }

  /**
   * API to download file from S3.
   *
   * @param bucketName name of the bucket
   * @param key file location with name
   * @return Response entity with status code 200 and file to download
   */
  @GetMapping("/download")
  public ResponseEntity downloadFile(@RequestParam("bucketName") final String bucketName,
      @RequestParam("key") final String key) {

    if (Objects.nonNull(bucketName) && Objects.nonNull(key)) {
      log.info("Request receive to download file: {} from bucket: {}", key, bucketName);
      final var storageDto = StorageDto.builder().bucketName(bucketName)
          .key(key).build();
      final var response = awsStorageService.download(storageDto);
      final ByteArrayResource resource = new ByteArrayResource(response);
      return ResponseEntity
          .ok()
          .contentLength(response.length)
          .header(CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
          .header(CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
          .body(resource);
    } else {
      throw new AwsStorageException(
          "Bucket Name and Key both parameters required to serve download");
    }
  }

  /**
   * API for list files from S3.
   *
   * @param bucketName name of the bucket
   * @param folderPath folder path from where files location has to be fetch.
   * @return Response entity with code 200 and object summaries
   */
  @GetMapping("/list")
  public ResponseEntity listFiles(@RequestParam("bucketName") final String bucketName,
      @RequestParam("folderPath") final String folderPath) {

    if (Objects.nonNull(bucketName) && Objects.nonNull(folderPath)) {
      log.info("Request receive to download files from bucket: {} and folder location: {}",
          bucketName, folderPath);
      final var storageDto = StorageDto.builder().bucketName(bucketName)
          .folderPath(folderPath).build();
      final var objectSummaries = awsStorageService.listFiles(storageDto);
      return ResponseEntity.ok().body(objectSummaries);
    } else {
      throw new AwsStorageException(
          "Bucket Name and Folder Path, all parameters required to serve list");
    }
  }
}
