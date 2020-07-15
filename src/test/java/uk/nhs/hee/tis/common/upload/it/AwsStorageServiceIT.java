package uk.nhs.hee.tis.common.upload.it;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.amazonaws.services.s3.AmazonS3;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import uk.nhs.hee.tis.common.upload.CommonUploadApplication;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

@SpringBootTest(classes = CommonUploadApplication.class)
@TestPropertySource("classpath:application-int.yml")
@ActiveProfiles("int")
class AwsStorageServiceIT {

  @Autowired
  private AwsStorageService storageService;

  @Autowired
  private AmazonS3 amazonS3;

  private String bucketName;
  private String folderPath;
  private String fileName;
  private String fileContent;
  private String key;

  @BeforeEach
  public void setup() {
    fileName = "test.txt";
    bucketName = "tis-test-bucket-2020";
    folderPath = "1/concern";
    fileContent = "this is the file";
    key = String.format("%s/%s", folderPath, fileName);
  }

  @Test
  void shouldUploadFileToS3() throws IOException {
    final var mockMultipartFile = new MockMultipartFile(fileName, fileName,
        "", fileContent.getBytes());
    final var fileUploadDto = StorageDto.builder().bucketName(bucketName)
        .folderPath(folderPath).file(mockMultipartFile).build();

    final var putObjectResult = storageService.upload(fileUploadDto);
    assertThat(putObjectResult, is(notNullValue()));

    final var s3Object = amazonS3.getObject(bucketName, key);
    final var objectContent = s3Object.getObjectContent();
    final var content = IOUtils.toString(objectContent);

    assertThat(s3Object.getKey(), is(key));
    assertThat(s3Object.getBucketName(), is(bucketName));
    assertThat(content, is(fileContent));
  }

  @AfterEach
  public void tearDown() {
    amazonS3.deleteObject(bucketName, key);
  }
}
