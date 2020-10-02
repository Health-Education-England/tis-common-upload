package uk.nhs.hee.tis.common.upload.controller;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
  @PostMapping(value = "/upload")
  public ResponseEntity uploadFile(final StorageDto storageDto) {

    log.info("Request receive to upload file: {}", storageDto);
    if (Objects.nonNull(storageDto.getBucketName()) && Objects.nonNull(storageDto.getFolderPath())
        && Objects.nonNull(storageDto.getFiles()) && !storageDto.getFiles().isEmpty()) {
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
   * @param key        file location with name
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
   * Get an object as a formatted string.
   *
   * @param bucketName name of the bucket
   * @param key        file location with name
   * @return Response entity with status code 200 and the object
   */
  @GetMapping("/data")
  public ResponseEntity<String> getData(@RequestParam("bucketName") final String bucketName,
      @RequestParam("key") final String key) {

    log.info("Request receive to download file: {} from bucket: {}", key, bucketName);
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    String responseJson = awsStorageService.getData(storageDto);
    return ResponseEntity
        .ok().body(responseJson);
  }

  /**
   * List the contents of a folder.
   *
   * @param bucketName            name of the bucket
   * @param folderPath            name of the folder
   * @param includeCustomMetaData include all custom metadata
   * @return a list of the objects at the requested location
   */
  @GetMapping("/list")
  public ResponseEntity listFiles(@RequestParam("bucketName") final String bucketName,
      @RequestParam("folderPath") final String folderPath,
      @RequestParam(value = "includeCustomMetadata", required = false)
      final boolean includeCustomMetaData) {

    if (Objects.nonNull(bucketName) && Objects.nonNull(folderPath)) {
      log.info("Request receive to download files from bucket: {} and folder location: {}",
          bucketName, folderPath);
      final var storageDto = StorageDto.builder().bucketName(bucketName)
          .folderPath(folderPath).build();
      final var objectSummaries = awsStorageService.listFiles(storageDto, includeCustomMetaData);
      return ResponseEntity.ok().body(objectSummaries);
    } else {
      throw new AwsStorageException(
          "Bucket Name and Folder Path, all parameters required to serve list");
    }
  }

  /**
   * Delete an object from S3.
   *
   * @param bucketName The bucket to find the object in
   * @param key        The key for the object tod be deleted
   * @return HTTP OK and message confirming deletion when successful
   */
  @DeleteMapping("/delete")
  public ResponseEntity<String> delete(@RequestParam("bucketName") final String bucketName,
      @RequestParam("key") final String key) {

    if (Objects.nonNull(bucketName) && Objects.nonNull(key)) {
      log.info("Request receive to remove file: {} from bucket: {}", key, bucketName);
      final var storageDto = StorageDto.builder().bucketName(bucketName)
          .key(key).build();
      awsStorageService.delete(storageDto);
      final String response = "[" + key + "] deleted successfully.";
      return new ResponseEntity<>(response, HttpStatus.OK);
    } else {
      throw new AwsStorageException(
          "Bucket Name and Key both parameters required to serve download");
    }
  }
}
