package uk.nhs.hee.tis.common.upload.service;

import static java.lang.String.format;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@Slf4j
@Service
public class AwsStorageService {

  @Autowired
  private AmazonS3 amazonS3;

  /**
   *  Upload to S3.
   *
   * @param storageDto contains information about file
   * @return result of s3 put call.
   */
  public PutObjectResult upload(final StorageDto storageDto) {
    final var bucketName = storageDto.getBucketName();
    final var folderPath = storageDto.getFolderPath();
    final var file = storageDto.getFile();

    try {
      createBucketIfNotExist(bucketName);
      final var key = format("%s/%s", folderPath, file.getOriginalFilename());
      final var request = new PutObjectRequest(bucketName, key, file.getInputStream(),
          new ObjectMetadata());
      log.info("uploading file: {} to bucket: {} with key: {}", file.getName(), bucketName, key);
      return amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Fail to upload file: {} in bucket: {}", file.getOriginalFilename(), bucketName);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * Download file from S3.
   *
   * @param storageDto contains information about file
   * @return file which will be downloaded
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
   * Fetch list of files from S3.
   *
   * @param storageDto contains information about file
   * @return List of S3 object summaries.
   */
  public List<S3ObjectSummary> listFiles(final StorageDto storageDto) {
    try {
      final var listObjects = amazonS3
          .listObjects(storageDto.getBucketName(), storageDto.getFolderPath() + "/");
      return listObjects.getObjectSummaries();
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
}
