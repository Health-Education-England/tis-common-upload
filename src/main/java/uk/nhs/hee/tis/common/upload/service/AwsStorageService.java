package uk.nhs.hee.tis.common.upload.service;

import static java.lang.String.format;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.common.upload.dto.FileSummaryDto;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@Slf4j
@Service
public class AwsStorageService {

  private static final String USER_METADATA_FILE_NAME = "name";
  private static final String USER_METADATA_FILE_TYPE = "type";

  @Autowired
  private AmazonS3 amazonS3;

  public PutObjectResult upload(final StorageDto storageDto) {
    final var bucketName = storageDto.getBucketName();
    final var folderPath = storageDto.getFolderPath();
    final var file = storageDto.getFile();

    try {
      createBucketIfNotExist(bucketName);
      final var key = format("%s/%s", folderPath, file.getOriginalFilename());
      final var metadata = new ObjectMetadata();
      metadata.addUserMetadata(USER_METADATA_FILE_NAME, file.getOriginalFilename());
      metadata.addUserMetadata(USER_METADATA_FILE_TYPE, getExtension(file.getOriginalFilename()));
      final var request = new PutObjectRequest(bucketName, key, file.getInputStream(), metadata);
      log.info("uploading file: {} to bucket: {} with key: {}", file.getName(), bucketName, key);
      return amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Fail to upload file: {} in bucket: {}", file.getOriginalFilename(), bucketName);
      throw new AwsStorageException(e.getMessage());
    }
  }

  public byte[] download(final StorageDto storageDto) {
    try {
      log.info("Download file: {} from bucket: {} with key: {}", storageDto.getKey(),
          storageDto.getBucketName(), storageDto.getKey());
      final var s3Object = amazonS3.getObject(storageDto.getBucketName(), storageDto.getKey());
      final var inputStream = s3Object.getObjectContent();
      final var content = IOUtils.toByteArray(inputStream);
      log.info("File downloaded successfully.");
      s3Object.close();
      return content;
    } catch (Exception e) {
      log.error("Fail to download file: {} from bucket: {}", storageDto.getKey(),
          storageDto.getBucketName());
      throw new AwsStorageException(e.getMessage());
    }
  }

  public List<FileSummaryDto> listFiles(final StorageDto storageDto) {
    try {
      final var listObjects = amazonS3
          .listObjects(storageDto.getBucketName(), storageDto.getFolderPath() + "/");
      final var fileSummaries = listObjects.getObjectSummaries().stream().map(summary -> {
        return buildFileSummary(summary);
      }).collect(Collectors.toList());
      return fileSummaries;
    } catch (Exception e) {
      log.error("Fail to list files from bucket: {} with folderPath: {}",
          storageDto.getBucketName(), storageDto.getFolderPath());
      throw new AwsStorageException(e.getMessage());
    }
  }

  private void createBucketIfNotExist(final String bucketName) {
    if (!amazonS3.doesBucketExistV2(bucketName)) {
      amazonS3.createBucket(bucketName);
    }
  }

  private FileSummaryDto buildFileSummary(final S3ObjectSummary summary) {
    final var objectMetadata = amazonS3
        .getObjectMetadata(summary.getBucketName(), summary.getKey());
    log.debug("Metadata details for file:{}, Metadata: {}", summary.getKey(), objectMetadata);
    return FileSummaryDto.builder()
        .bucketName(summary.getBucketName())
        .key(summary.getKey())
        .fileName(objectMetadata.getUserMetaDataOf(USER_METADATA_FILE_NAME))
        .fileType(objectMetadata.getUserMetaDataOf(USER_METADATA_FILE_TYPE))
        .build();
  }

  public void delete(final StorageDto storageDto) {
    try {
      log.info("Remove file: {} from bucket: {} with key: {}", storageDto.getKey(),
          storageDto.getBucketName(), storageDto.getKey());
      amazonS3.deleteObject(storageDto.getBucketName(), storageDto.getKey());
      log.info("File is removed successfully.");
    } catch (Exception e) {
      log.error("Fail to delete file from bucket: {} with key: {}",
          storageDto.getBucketName(), storageDto.getKey());
      throw new AwsStorageException(e.getMessage());
    }
  }
}
