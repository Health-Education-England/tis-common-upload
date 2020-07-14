package uk.nhs.hee.tis.common.upload.service;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.github.javafaker.Faker;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import uk.nhs.hee.tis.common.upload.dto.DownloadDto;
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

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

  private String fileName;
  private String bucketName;
  private String folderName;
  private String fileContent;

  @BeforeEach
  public void setup() {
    fileName = faker.lorem().characters(10);
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
    fileContent = faker.lorem().sentence(5);
  }

  @Test
  public void shouldUploadFile() throws IOException {
    final var fileUploadDto = FileUploadDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .file(file)
        .build();
    when(file.getOriginalFilename()).thenReturn(fileName);
    when(file.getInputStream()).thenReturn(inputStream);
    when(amazonS3.putObject(any())).thenReturn(result);
    final var putObjectResult = awsStorageService.upload(fileUploadDto);
    assertThat(putObjectResult, notNullValue());
  }

  @Test
  public void shouldHandleExceptionIfUploadFails() throws IOException {
    final var fileUploadDto = FileUploadDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .file(file)
        .build();
    when(file.getOriginalFilename()).thenReturn(fileName);
    when(file.getInputStream()).thenReturn(inputStream);
    when(amazonS3.putObject(any())).thenThrow(AmazonServiceException.class);
    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.upload(fileUploadDto);
    });
  }

  @Test
  public void shouldDownloadFileFromS3() {
    final var downloadDto = DownloadDto.builder().bucketName(bucketName).folderPath(folderName)
        .fileName(fileName).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    final var key = format("%s/%s", downloadDto.getFolderPath(), downloadDto.getFileName());
    when(amazonS3.getObject(bucketName, key)).thenReturn(s3Object);
    final var byteArray = awsStorageService.download(downloadDto);
    final var downloadedContent = new String(byteArray);

    assertThat(downloadedContent, is(fileContent));
  }

  @Test
  public void shouldThrowExceptionWhenDownloadFileNotFound() {
    final var downloadDto = DownloadDto.builder().bucketName(bucketName).folderPath(folderName)
        .fileName(fileName).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    final var key = format("%s/%s", downloadDto.getFolderPath(), downloadDto.getFileName());
    when(amazonS3.getObject(bucketName, key)).thenThrow(AmazonServiceException.class);

    Assertions.assertThrows(AwsStorageException.class, () -> {
      awsStorageService.download(downloadDto);
    });
  }

}
