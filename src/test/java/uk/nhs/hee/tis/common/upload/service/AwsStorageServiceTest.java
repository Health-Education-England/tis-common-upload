/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.common.upload.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.enumeration.DeleteType;
import uk.nhs.hee.tis.common.upload.enumeration.LifecycleState;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
public class AwsStorageServiceTest {

  private final Faker faker = new Faker();

  private AwsStorageService awsStorageService;

  private AmazonS3 s3Mock;

  private AwsSnsService snsMock;

  @Mock
  private MultipartFile file1Mock;

  @Mock
  private MultipartFile file2Mock;

  @Mock
  private InputStream inputStreamMock;

  @Mock
  private PutObjectResult resultMock;

  @Mock
  private ObjectListing objectListingMock;

  @Mock
  private ObjectMetadata metadataMock;
  @Mock
  private ObjectMetadata metadataMock2;
  @Mock
  private ObjectMetadata metadataMock3;
  @Mock
  private ObjectMetadata metadataMock4;

  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  @Captor
  private ArgumentCaptor<DeleteEventDto> deleteEventCaptor;

  private String fileName;
  private String bucketName;
  private String folderName;
  private String fileContent;
  private String jsonFileContent;
  private String key;
  private Map<String, String> customMetadata;
  private ObjectMetadata objectJsonMetadata;
  private S3VersionSummary versionSummary1;
  private S3VersionSummary versionSummary2;
  private S3VersionSummary versionSummary3;
  private VersionListing versions;
  private BucketVersioningConfiguration bucketVersioningConfiguration;

  @BeforeEach
  void setup() {
    s3Mock = mock(AmazonS3.class);
    snsMock = mock(AwsSnsService.class);
    awsStorageService = new AwsStorageService(s3Mock, snsMock, new ObjectMapper());

    fileName = faker.lorem().characters(10);
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
    fileContent = faker.lorem().sentence(5);
    key = faker.lorem().characters(10);
    customMetadata = Map.of("answer", "42", "uploadedBy", "Marvin");

    jsonFileContent =  "{\"id\":\"1\",\"lifecycleState\":\"SUBMITTED\",\"forename\":\"forename\"}";

    bucketVersioningConfiguration = new BucketVersioningConfiguration();
    bucketVersioningConfiguration.setStatus(BucketVersioningConfiguration.ENABLED);

    objectJsonMetadata = new ObjectMetadata();
    objectJsonMetadata.addUserMetadata("type", "json");
    objectJsonMetadata.addUserMetadata("deletetype", DeleteType.PARTIAL.name());
    objectJsonMetadata.addUserMetadata("fixedfields", "id,lifecycleState");
    objectJsonMetadata.addUserMetadata("lifecyclestate", LifecycleState.SUBMITTED.name());

    versionSummary1 = new S3VersionSummary();
    versionSummary1.setVersionId("1");
    versionSummary1.setIsLatest(true);

    versionSummary2 = new S3VersionSummary();
    versionSummary2.setVersionId("2");
    versionSummary2.setIsLatest(false);

    versionSummary3 = new S3VersionSummary();
    versionSummary3.setVersionId("3");
    versionSummary3.setIsLatest(false);
    versions = new VersionListing();
    versions.setVersionSummaries(List.of(versionSummary1, versionSummary2, versionSummary3));
  }

  @Test
  void shouldUploadFile() throws IOException {
    when(file1Mock.getOriginalFilename()).thenReturn(fileName);
    when(file2Mock.getOriginalFilename()).thenReturn(fileName);
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(file2Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(putRequestCaptor.capture())).thenReturn(resultMock);

    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock, file2Mock)).customMetadata(customMetadata).build();
    final var putObjectResult = awsStorageService.upload(storageDto);

    assertThat(putObjectResult, hasSize(2));
    var actualUserMetadata = putRequestCaptor.getValue().getMetadata().getUserMetadata().entrySet();
    customMetadata.entrySet().forEach(entry -> assertThat(actualUserMetadata, hasItem(entry)));
  }

  @Test
  void shouldHandleExceptionIfUploadFails() {
    when(s3Mock.putObject(any())).thenThrow(AmazonServiceException.class);

    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock)).build();

    assertThrows(AwsStorageException.class, () -> awsStorageService.upload(storageDto));
  }

  @Test
  void shouldDownloadFileFromS3() {
    final var s3Object = createObject(null, null, fileContent);
    when(s3Mock.getObject(bucketName, key)).thenReturn(s3Object);

    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    final var byteArray = awsStorageService.download(storageDto);
    final var downloadedContent = new String(byteArray);

    assertThat(downloadedContent, is(fileContent));
  }

  @Test
  void shouldThrowExceptionWhenDownloadFileNotFound() {
    when(s3Mock.getObject(bucketName, key)).thenThrow(AmazonServiceException.class);

    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    assertThrows(AwsStorageException.class, () -> awsStorageService.download(storageDto));
  }


  @Test
  void getDataShouldReturnExpectedData() {
    final S3Object stubbedValue = createObject(bucketName, key, fileContent);
    when(s3Mock.getObject(bucketName, key)).thenReturn(stubbedValue);

    final StorageDto input = StorageDto.builder().bucketName(bucketName).key(key).build();
    final String actual = awsStorageService.getData(input);

    assertEquals(fileContent, actual);
  }

  @Test
  void getDataShouldWrapException() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    String expectedMessage = "Expected Exception";
    when(s3Mock.getObject(bucketName, key)).thenThrow(new AmazonServiceException(
        expectedMessage));

    Throwable actual =
        assertThrows(AwsStorageException.class, () -> awsStorageService.getData(storageDto));
    assertThat(actual.getMessage(), startsWith(expectedMessage));
  }

  @Test
  void shouldListFilesFromS3() {
    final var key = folderName + "/test.txt";
    final S3ObjectSummary s3ObjectSummary = createSummary(bucketName, key);
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    expectMetadataInteractions(bucketName, key, metadataMock, "test.txt", "txt");
    when(metadataMock.getUserMetadata()).thenReturn(Map.of("destination", "unknown"));

    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var objectSummaries = awsStorageService.listFiles(storageDto, true, null);

    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
    assertThat(objectSummaries.get(0).getFileName(), is("test.txt"));
    assertThat(objectSummaries.get(0).getFileType(), is("txt"));
    assertThat(objectSummaries.get(0).getCustomMetadata().get("destination"), is("unknown"));
  }

  @Test
  void shouldListFilesFromS3WithoutMetadata() {
    final var key = folderName + "/test.txt";
    final S3ObjectSummary s3ObjectSummary = createSummary(bucketName, key);
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    expectMetadataInteractions(bucketName, key, metadataMock, null, null);

    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var objectSummaries = awsStorageService.listFiles(storageDto, false, null);
    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
    assertThat(objectSummaries.get(0).getFileName(), is(nullValue()));
    assertThat(objectSummaries.get(0).getCustomMetadata(), is(nullValue()));
    verify(metadataMock, never()).getUserMetadata();
  }

  @Test
  void shouldThrowExceptionWhenListNotFound() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .build();
    when(s3Mock.listObjects(bucketName, folderName + "/"))
        .thenThrow(AmazonServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.listFiles(storageDto, false,
        null));
  }

  @Test
  void shouldDeleteFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    awsStorageService.delete(storageDto);
    verify(s3Mock).deleteObject(bucketName, key);

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.HARD));
  }

  @Test
  void shouldHardDeleteIfDeleteTypeIsHard() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    objectJsonMetadata.addUserMetadata("deletetype", DeleteType.HARD.name());

    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(objectJsonMetadata);
    awsStorageService.delete(storageDto);
    verify(s3Mock).deleteObject(bucketName, key);

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.HARD));
  }

  @Test
  void shouldThrowExceptionWhenFailToHardDeleteFile() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    doThrow(AmazonServiceException.class).when(s3Mock).deleteObject(bucketName, key);
    assertThrows(AwsStorageException.class, () -> awsStorageService.delete(storageDto));
  }

  @Test
  void shouldPartialDeleteFileFromS3() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();

    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(objectJsonMetadata);
    final S3Object s3Object = createObject(bucketName, key, jsonFileContent);
    when(s3Mock.getObject(bucketName, key)).thenReturn(s3Object);
    when(s3Mock.getBucketVersioningConfiguration(bucketName))
        .thenReturn(bucketVersioningConfiguration);
    when(s3Mock.listVersions(bucketName, key)).thenReturn(versions);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.getBucketName(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.getKey(), is(key));

    final var resultInputStream = putObjectRequest.getInputStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is(LifecycleState.DELETED.name()));

    final var resultUserMetadata = putObjectRequest.getMetadata().getUserMetadata();
    objectJsonMetadata.getUserMetadata().entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteVersion(bucketName, key, "1");
    verify(s3Mock).deleteVersion(bucketName, key, "2");
    verify(s3Mock).deleteVersion(bucketName, key, "3");

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.PARTIAL));
  }

  @Test
  void shouldNotUpdateFileContentIfPartialDeleteFileTypeNotJson() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();

    objectJsonMetadata.addUserMetadata("type", "doc");

    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(objectJsonMetadata);
    final S3Object s3Object = createObject(bucketName, key, jsonFileContent);
    when(s3Mock.getObject(bucketName, key)).thenReturn(s3Object);
    when(s3Mock.getBucketVersioningConfiguration(bucketName))
        .thenReturn(bucketVersioningConfiguration);
    when(s3Mock.listVersions(bucketName, key)).thenReturn(versions);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.getBucketName(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.getKey(), is(key));

    final var resultInputStream = putObjectRequest.getInputStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is(LifecycleState.SUBMITTED.name()));
    assertThat("Unexpected input stream.", resultJsonMap.get("forename"), is("forename"));

    final var resultUserMetadata = putObjectRequest.getMetadata().getUserMetadata();
    objectJsonMetadata.getUserMetadata().entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteVersion(bucketName, key, "1");
    verify(s3Mock).deleteVersion(bucketName, key, "2");
    verify(s3Mock).deleteVersion(bucketName, key, "3");

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.PARTIAL));
  }

  @Test
  void shouldNotDeleteVersionIfPartialDeleteVersioningNotEnable() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();

    bucketVersioningConfiguration.setStatus(BucketVersioningConfiguration.OFF);

    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(objectJsonMetadata);
    final S3Object s3Object = createObject(bucketName, key, jsonFileContent);
    when(s3Mock.getObject(bucketName, key)).thenReturn(s3Object);
    when(s3Mock.getBucketVersioningConfiguration(bucketName))
        .thenReturn(bucketVersioningConfiguration);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.getBucketName(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.getKey(), is(key));

    final var resultInputStream = putObjectRequest.getInputStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is(LifecycleState.DELETED.name()));

    final var resultUserMetadata = putObjectRequest.getMetadata().getUserMetadata();
    objectJsonMetadata.getUserMetadata().entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteVersion(bucketName, key, "1");
    verify(s3Mock, never()).deleteVersion(bucketName, key, "2");
    verify(s3Mock, never()).deleteVersion(bucketName, key, "3");

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.PARTIAL));
  }

  private S3Object createObject(String bucketName, String key, String fileContent) {
    S3Object obj = new S3Object();
    obj.setBucketName(bucketName);
    obj.setKey(key);
    obj.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    return obj;
  }

  private S3ObjectSummary createSummary(String bucketName, String objectKey) {
    S3ObjectSummary summary = new S3ObjectSummary();
    summary.setBucketName(bucketName);
    summary.setKey(objectKey);
    return summary;
  }

  private void expectMetadataInteractions(String objectBucketName, String objectKey,
      ObjectMetadata objectMetadata, String objectName, String objectType) {
    when(s3Mock.getObjectMetadata(objectBucketName, objectKey)).thenReturn(objectMetadata);
    when(objectMetadata.getUserMetaDataOf("name")).thenReturn(objectName);
    when(objectMetadata.getUserMetaDataOf("type")).thenReturn(objectType);
  }

  @Nested
  class SortingTest {

    @Test
    void listFilesShouldSort() {
      S3ObjectSummary s3Object1 = createSummary(bucketName, key + "1");
      S3ObjectSummary s3Object2 = createSummary(bucketName, key + "2");
      S3ObjectSummary s3Object3 = createSummary(bucketName, key + "3");
      S3ObjectSummary s3Object4 = createSummary(bucketName, key + "nullName");
      when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
      when(objectListingMock.getObjectSummaries()).thenReturn(
          Arrays.asList(s3Object2, s3Object4, s3Object1, s3Object3));
      String test1Name = "test1.foo";
      expectMetadataInteractions(bucketName, key + "1", metadataMock, test1Name, null);
      String test2Name = "test2.foo";
      expectMetadataInteractions(bucketName, key + "2", metadataMock2, test2Name, null);
      String test3Name = "test3.foo";
      expectMetadataInteractions(bucketName, key + "3", metadataMock3, test3Name, null);
      expectMetadataInteractions(bucketName, key + "nullName", metadataMock4, null, null);

      final StorageDto storageDto = StorageDto.builder().bucketName(bucketName)
          .folderPath(folderName).build();
      final var actualFileSummaryList = awsStorageService
          .listFiles(storageDto, false, "fileName,desc");

      assertThat(actualFileSummaryList, hasSize(4));
      assertThat(actualFileSummaryList.get(0).getFileName(), is(test3Name));
      assertThat(actualFileSummaryList.get(1).getFileName(), is(test2Name));
      assertThat(actualFileSummaryList.get(2).getFileName(), is(test1Name));
      assertThat(actualFileSummaryList.get(3).getFileName(), nullValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonexistantProp,asc", "filename,asc,extraBit"})
    void listFilesWithBadSortShouldStillReturn(String sort) {
      S3ObjectSummary s3Object1 = createSummary(bucketName, key + "1");
      S3ObjectSummary s3Object4 = createSummary(bucketName, key + "nullName");
      when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
      when(objectListingMock.getObjectSummaries()).thenReturn(
          Arrays.asList(s3Object4, s3Object1));
      expectMetadataInteractions(bucketName, key + "1", metadataMock, null, null);
      expectMetadataInteractions(bucketName, key + "nullName", metadataMock, null, null);

      final StorageDto storageDto = StorageDto.builder().bucketName(bucketName)
          .folderPath(folderName).build();
      final var actualList = awsStorageService.listFiles(storageDto, false, sort);

      assertThat(actualList, hasSize(2));
    }
  }

}
