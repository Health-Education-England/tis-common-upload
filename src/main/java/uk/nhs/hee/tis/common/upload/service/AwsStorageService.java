package uk.nhs.hee.tis.common.upload.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.List;
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

  /**
   * Upload files in the bucket with a prefix of folderPath, specified in {@code storageDto}.
   *
   * @param storageDto representation of files to be uploaded to S3
   * @return result of attempts to store the objects
   */
  public List<PutObjectResult> upload(final StorageDto storageDto) {
    final var bucketName = storageDto.getBucketName();
    final var folderPath = storageDto.getFolderPath();
    final var files = storageDto.getFiles();

    createBucketIfNotExist(bucketName);

    return files.stream().map(file -> {
      try {
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
    }).collect(toList());
  }

  /**
   * Get the object contents as bytes.
   *
   * @param storageDto holder for the bucket and object key
   * @return byte array of the object content
   */
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

  /**
   * Get the object contents as a string.
   *
   * @param storageDto holder for the bucket and object key
   * @return text string of the object content
   */
  public String getData(StorageDto storageDto) {
    try (S3Object object = amazonS3.getObject(storageDto.getBucketName(), storageDto.getKey())) {
      return IOUtils.toString(object.getObjectContent());
    } catch (Exception e) {
      log.error("Unable to retrieve object from S3 as a String", e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * List objects in a bucket under a given prefix.
   *
   * @param storageDto      holder for the bucket and folderPath (key prefix)
   * @param includeMetadata whether all custom metadata should be included
   * @return a list of summaries for objects which were found
   */
  public List<FileSummaryDto> listFiles(final StorageDto storageDto,
      final boolean includeMetadata) {
    try {
      final var listObjects = amazonS3
          .listObjects(storageDto.getBucketName(), storageDto.getFolderPath() + "/");
      return listObjects.getObjectSummaries().stream()
          .map(summary -> buildFileSummary(summary, includeMetadata)).collect(toList());
    } catch (Exception e) {
      log.error("Fail to list files from bucket: {} with folderPath: {}",
          storageDto.getBucketName(), storageDto.getFolderPath());
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * Delete the object identified by a key in a bucket.
   *
   * @param storageDto holder for the bucket and object key
   * @throws AwsStorageException if there is a problem deleting the object
   */
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

  private void createBucketIfNotExist(final String bucketName) {
    if (!amazonS3.doesBucketExistV2(bucketName)) {
      amazonS3.createBucket(bucketName);
    }
  }

  private FileSummaryDto buildFileSummary(final S3ObjectSummary summary,
      final boolean includeCustomMetadata) {
    final var objectMetadata = amazonS3
        .getObjectMetadata(summary.getBucketName(), summary.getKey());
    log.debug("Metadata details for file:{}, Metadata: {}", summary.getKey(), objectMetadata);
    return FileSummaryDto.builder()
        .bucketName(summary.getBucketName())
        .key(summary.getKey())
        .fileName(objectMetadata.getUserMetaDataOf(USER_METADATA_FILE_NAME))
        .fileType(objectMetadata.getUserMetaDataOf(USER_METADATA_FILE_TYPE))
        .customMetadata(includeCustomMetadata ? objectMetadata.getUserMetadata() : null)
        .build();
  }
}
