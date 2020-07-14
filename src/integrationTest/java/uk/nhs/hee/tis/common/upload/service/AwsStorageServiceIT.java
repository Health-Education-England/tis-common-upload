package uk.nhs.hee.tis.common.upload.service;

import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.hee.tis.common.upload.CommonUploadApplication;
import uk.nhs.hee.tis.common.upload.IntegrationTest;
import uk.nhs.hee.tis.common.upload.config.AwsS3Config;

@SpringBootTest(classes = { CommonUploadApplication.class, AwsS3Config.class })
@IntegrationTest
public class AwsStorageServiceIT {

//  @Autowired
//  private AwsStorageService storageService;
//
//  @Autowired
//  private AmazonS3 amazonS3;
//
//  private String bucketName;
//  private String folderPath;
//  private String fileName;
//  private String fileContent;
//  private String key;
//
//  @BeforeEach
//  public void setup() {
//    fileName = "test.txt";
//    bucketName = "tis-test-bucket-2020";
//    folderPath = "1/concern";
//    fileContent = "this is the file";
//    key = String.format("%s/%s", folderPath, fileName);
//  }
//
//  @Test
//  public void shouldUploadFileToS3() throws IOException {
//    final var mockMultipartFile = new MockMultipartFile(fileName, fileName,
//        "", fileContent.getBytes());
//    final var fileUploadDto = FileUploadDto.builder().bucketName(bucketName)
//        .folderPath(folderPath).file(mockMultipartFile).build();
//
//    final var putObjectResult = storageService.upload(fileUploadDto);
//    assertThat(putObjectResult, is(notNullValue()));
//
//    final var s3Object = amazonS3.getObject(bucketName, key);
//    final var objectContent = s3Object.getObjectContent();
//    final var content = IOUtils.toString(objectContent);
//
//    assertThat(s3Object.getKey(), is(key));
//    assertThat(s3Object.getBucketName(), is(bucketName));
//    assertThat(content, is(fileContent));
//  }
//
//  @AfterEach
//  public void tearDown() {
//    amazonS3.deleteObject(bucketName, key);
//  }
}
