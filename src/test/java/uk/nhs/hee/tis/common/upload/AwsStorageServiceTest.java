package uk.nhs.hee.tis.common.upload;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.github.javafaker.Faker;
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
import uk.nhs.hee.tis.common.upload.dto.FileUploadDto;
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

  private String fileName;
  private String bucketName;
  private String folderName;

  @BeforeEach
  public void setup() {
    fileName = faker.lorem().characters(10);
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
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

}
