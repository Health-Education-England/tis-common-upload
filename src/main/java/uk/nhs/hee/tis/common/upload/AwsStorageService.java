package uk.nhs.hee.tis.common.upload;

import static java.lang.String.format;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@Slf4j
@Service
public class AwsStorageService {

  @Autowired
  private AmazonS3 amazonS3;

  public PutObjectResult upload(final FileUploadDto fileUploadDto) {
    final var bucketName = fileUploadDto.getBucketName();
    final var folderPath = fileUploadDto.getFolderPath();
    final var file = fileUploadDto.getFile();

    try {
      createBucketIfNotExist(bucketName);
      final var key = format("%s/%s", folderPath, file.getOriginalFilename());
      final var request = new PutObjectRequest(bucketName, key, file.getInputStream(),
          new ObjectMetadata());
      log.info("uploading file[{}] to bucket[{}] with key[{}]", file.getName(), bucketName, key);
      return amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Fail to upload file [{}] in bucket [{}]", file.getOriginalFilename(), bucketName);
      throw new AwsStorageException(e.getMessage());
    }
  }

  private void createBucketIfNotExist(final String bucketName) {
    if (!amazonS3.doesBucketExistV2(bucketName)) {
      amazonS3.createBucket(bucketName);
    }
  }
}
