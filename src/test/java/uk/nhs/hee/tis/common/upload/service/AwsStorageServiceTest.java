package uk.nhs.hee.tis.common.upload.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.github.javafaker.Faker;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@ExtendWith(MockitoExtension.class)
public class AwsStorageServiceTest {

  private final Faker faker = new Faker();

  @InjectMocks
  private AwsStorageService awsStorageService;

  @Mock
  private AmazonS3 amazonS3;

  @Mock
  private MultipartFile file;

  @Mock
  private InputStream inputStream;

  @Mock
  private PutObjectResult result;

  @Mock
  private ObjectListing objectListing;

  private String fileName;
  private String bucketName;
  private String folderName;
  private String fileContent;
  private String key;

  @BeforeEach
  public void setup() {
    fileName = faker.lorem().characters(10);
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
    fileContent = faker.lorem().sentence(5);
    key = faker.lorem().characters(10);
  }

  @Test
  public void shouldUploadFile() throws IOException {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .file(file)
        .build();
    when(file.getOriginalFilename()).thenReturn(fileName);
    when(file.getInputStream()).thenReturn(inputStream);
    when(amazonS3.putObject(any())).thenReturn(result);
    final var putObjectResult = awsStorageService.upload(storageDto);
    assertThat(putObjectResult, notNullValue());
  }

  @Test
  public void shouldHandleExceptionIfUploadFails() throws IOException {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .file(file)
        .build();
    when(file.getOriginalFilename()).thenReturn(fileName);
    when(file.getInputStream()).thenReturn(inputStream);
    when(amazonS3.putObject(any())).thenThrow(AmazonServiceException.class);
    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.upload(storageDto);
    });
  }

  @Test
  public void shouldDownloadFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(amazonS3.getObject(bucketName, key)).thenReturn(s3Object);
    final var byteArray = awsStorageService.download(storageDto);
    final var downloadedContent = new String(byteArray);

    assertThat(downloadedContent, is(fileContent));
  }

  @Test
  public void shouldThrowExceptionWhenDownloadFileNotFound() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(fileName).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(amazonS3.getObject(bucketName, key)).thenThrow(AmazonServiceException.class);

    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.download(storageDto);
    });
  }

  @Test
  public void shouldListFilesFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var key = folderName + "/test.txt";
    final var s3ObjectSummary = new S3ObjectSummary();
    s3ObjectSummary.setBucketName(bucketName);
    s3ObjectSummary.setKey(key);
    when(amazonS3.listObjects(bucketName, folderName + "/")).thenReturn(objectListing);
    when(objectListing.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));

    final var objectSummaries = awsStorageService.listFiles(storageDto);
    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
  }

  @Test
  public void shouldThrowExceptionWhenListNotFound() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .build();
    when(amazonS3.listObjects(bucketName, folderName + "/"))
        .thenThrow(AmazonServiceException.class);
    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.listFiles(storageDto);
    });
  }

  @Test
  public void shouldDeleteFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    awsStorageService.delete(storageDto);
    verify(amazonS3).deleteObject(bucketName, key);
  }

  @Test
  public void shouldThrowExceptionWhenFailToDeleteFile() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    doThrow(AmazonServiceException.class).when(amazonS3).deleteObject(bucketName, key);
    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.delete(storageDto);
    });
  }

}
