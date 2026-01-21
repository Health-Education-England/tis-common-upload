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

import static java.lang.String.format;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.enumeration.DeleteType;
import uk.nhs.hee.tis.common.upload.enumeration.LifecycleState;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

@ExtendWith(MockitoExtension.class)
class AwsStorageServiceTest {

  private final Faker faker = new Faker();

  private AwsStorageService awsStorageService;

  private S3Client s3Mock;

  private AwsSnsService snsMock;

  @Mock
  private MultipartFile file1Mock;

  @Mock
  private MultipartFile file2Mock;

  @Mock
  private InputStream inputStreamMock;

  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  @Captor
  private ArgumentCaptor<DeleteEventDto> deleteEventCaptor;
  @Captor
  private ArgumentCaptor<HeadBucketRequest> headBucketRequestCaptor;
  @Captor
  private ArgumentCaptor<RequestBody> requestBodyCaptor;

  private String fileName;
  private String bucketName;
  private String folderName;
  private String fileContent;
  private String jsonFileContent;
  private String key;
  private Map<String, String> customMetadata;
  private HeadObjectResponse headObjectResponse;
  private ListObjectVersionsResponse versions;
  private GetBucketVersioningResponse getBucketVersioningResponse;
  private PutObjectResponse putObjectResponse;

  @BeforeEach
  void setup() {
    s3Mock = mock(S3Client.class);
    snsMock = mock(AwsSnsService.class);
    awsStorageService = new AwsStorageService(s3Mock, snsMock, new ObjectMapper());

    fileName = faker.lorem().characters(10) + ".json";
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
    fileContent = faker.lorem().sentence(5);
    key = faker.lorem().characters(10);
    customMetadata = Map.of("answer", "42", "uploadedBy", "Marvin");

    jsonFileContent = "{\"id\":\"1\",\"lifecycleState\":\"SUBMITTED\",\"forename\":\"forename\"}";

    getBucketVersioningResponse = GetBucketVersioningResponse.builder()
        .status(BucketVersioningStatus.ENABLED).build();
    putObjectResponse = PutObjectResponse.builder().build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("type", "json");
    metadata.put("deletetype", DeleteType.PARTIAL.name());
    metadata.put("fixedfields", "id,lifecycleState");
    metadata.put("lifecyclestate", "SUBMITTED");
    headObjectResponse = HeadObjectResponse.builder().metadata(metadata).build();

    ObjectVersion versionSummary1 = ObjectVersion.builder()
        .versionId("1")
        .isLatest(true)
        .build();

    ObjectVersion versionSummary2 = ObjectVersion.builder()
        .versionId("2")
        .isLatest(false)
        .build();

    ObjectVersion versionSummary3 = ObjectVersion.builder()
        .versionId("3")
        .isLatest(false)
        .build();
    versions = ListObjectVersionsResponse.builder()
        .versions(List.of(versionSummary1, versionSummary2, versionSummary3))
        .build();
  }

  @Test
  void shouldUploadFile() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock, file2Mock)).customMetadata(customMetadata).build();
    key = format("%s/%s", storageDto.getFolderPath(), fileName);

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse);
    when(file1Mock.getOriginalFilename()).thenReturn(fileName);
    when(file2Mock.getOriginalFilename()).thenReturn(fileName);
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(file2Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(putRequestCaptor.capture(), any(RequestBody.class))).thenReturn(
        putObjectResponse);

    final var putObjectResult = awsStorageService.upload(storageDto);

    assertThat(putObjectResult, hasSize(2));
    var actualUserMetadata = putRequestCaptor.getValue().metadata();
    customMetadata.entrySet().forEach(entry ->
        assertThat(actualUserMetadata.entrySet(), hasItem(entry)));
    headObjectResponse.metadata().entrySet()
        .forEach(entry -> assertThat(actualUserMetadata.entrySet(), hasItem(entry)));
  }

  @Test
  void shouldUploadFileWithoutNewCustomMetadata() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock, file2Mock)).customMetadata(null).build();
    key = format("%s/%s", storageDto.getFolderPath(), fileName);

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse);
    when(file1Mock.getOriginalFilename()).thenReturn(fileName);
    when(file2Mock.getOriginalFilename()).thenReturn(fileName);
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(file2Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(putRequestCaptor.capture(), any(RequestBody.class))).thenReturn(
        putObjectResponse);

    final var putObjectResult = awsStorageService.upload(storageDto);

    assertThat(putObjectResult, hasSize(2));
    var actualUserMetadata = putRequestCaptor.getValue().metadata();
    headObjectResponse.metadata().entrySet().stream()
        .forEach(entry -> assertThat(actualUserMetadata.entrySet(), hasItem(entry)));
  }

  @Test
  void shouldCreateBucketIfNotExistWhenUpload() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock, file2Mock)).customMetadata(customMetadata).build();
    key = format("%s/%s", storageDto.getFolderPath(), fileName);

    when(s3Mock.headBucket(headBucketRequestCaptor.capture())).thenThrow(
        NoSuchBucketException.builder().build());
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(HeadObjectResponse.builder()
        .build());
    when(file1Mock.getOriginalFilename()).thenReturn(fileName);
    when(file2Mock.getOriginalFilename()).thenReturn(fileName);
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(file2Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(putRequestCaptor.capture(), any(RequestBody.class))).thenReturn(
        putObjectResponse);

    final var putObjectResult = awsStorageService.upload(storageDto);

    verify(s3Mock).createBucket(CreateBucketRequest.builder().bucket(bucketName)
        .build());
    assertThat(putObjectResult, hasSize(2));
    var actualUserMetadata =
        putRequestCaptor.getValue().metadata();
    customMetadata.entrySet().forEach(entry ->
        assertThat(actualUserMetadata.entrySet(), hasItem(entry)));
    assertEquals(bucketName, headBucketRequestCaptor.getValue().bucket());
  }

  @Test
  void shouldHandleExceptionIfUploadFails() throws IOException {
    when(s3Mock.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
        .build());
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(
        AwsServiceException.class);
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .files(List.of(file1Mock)).build();

    assertThrows(AwsStorageException.class, () -> awsStorageService.upload(storageDto));
  }

  @Test
  void shouldDownloadFileFromS3() {
    final ResponseInputStream<GetObjectResponse> responseResponseInputStream = createObject(
        fileContent);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(responseResponseInputStream);

    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    final var byteArray = awsStorageService.download(storageDto);
    final var downloadedContent = new String(byteArray);

    assertThat(downloadedContent, is(fileContent));
  }

  @Test
  void shouldThrowExceptionWhenDownloadFileNotFound() {
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenThrow(AwsServiceException.class);

    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    assertThrows(AwsStorageException.class, () -> awsStorageService.download(storageDto));
  }

  @Test
  void getDataShouldReturnExpectedData() {
    final ResponseInputStream<GetObjectResponse> stubbedValue = createObject(fileContent);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(stubbedValue);

    final StorageDto input = StorageDto.builder().bucketName(bucketName).key(key).build();
    final String actual = awsStorageService.getData(input);

    assertEquals(fileContent, actual);
  }

  @Test
  void getDataShouldWrapException() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    String expectedMessage = "Expected Exception";
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenThrow(AwsServiceException.builder().message(expectedMessage).build());

    Throwable actual =
        assertThrows(AwsStorageException.class, () -> awsStorageService.getData(storageDto));
    assertThat(actual.getMessage(), startsWith(expectedMessage));
  }

  @Test
  void shouldListFilesFromS3() {
    key = folderName + "/test.txt";
    final S3Object s3ObjectSummary = createSummary(key);
    ListObjectsResponse listObjectsResponse = ListObjectsResponse.builder()
        .contents(List.of(s3ObjectSummary))
        .build();
    when(s3Mock.listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(folderName + "/")
        .build())).thenReturn(listObjectsResponse);
    HeadObjectResponse headObjectResp = HeadObjectResponse.builder()
        .metadata(Map.of("destination", "unknown")).build();
    expectMetadataInteractions(bucketName, key, headObjectResp, "test.txt", "txt");

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
    key = folderName + "/test.txt";
    final S3Object s3ObjectSummary = createSummary(key);
    ListObjectsResponse listObjectsResponse = ListObjectsResponse.builder()
        .contents(List.of(s3ObjectSummary))
        .build();
    when(s3Mock.listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(folderName + "/")
        .build())).thenReturn(listObjectsResponse);
    expectMetadataInteractions(bucketName, key, HeadObjectResponse.builder().build(), null, null);

    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var objectSummaries = awsStorageService.listFiles(storageDto, false, null);
    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
    assertThat(objectSummaries.get(0).getFileName(), is(nullValue()));
    assertThat(objectSummaries.get(0).getCustomMetadata(), is(nullValue()));
  }

  @Test
  void shouldThrowExceptionWhenListNotFound() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .build();
    when(s3Mock.listObjects(ListObjectsRequest.builder().bucket(bucketName).prefix(folderName + "/")
        .build()))
        .thenThrow(AwsServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.listFiles(storageDto, false,
        null));
  }

  @Test
  void shouldDeleteFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    HeadObjectResponse headObjectResponse1 = HeadObjectResponse.builder().build();
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse1);

    awsStorageService.delete(storageDto);
    verify(s3Mock).deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key)
        .build());

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
    HeadObjectResponse headObjectResponse1 = HeadObjectResponse.builder()
        .metadata(Map.of("deletetype", DeleteType.HARD.name())).build();
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse1);

    awsStorageService.delete(storageDto);
    verify(s3Mock).deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key)
        .build());

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
    HeadObjectResponse headObjectResponse1 = HeadObjectResponse.builder().build();
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse1);

    doThrow(AwsServiceException.class).when(s3Mock)
        .deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key)
            .build());
    assertThrows(AwsStorageException.class, () -> awsStorageService.delete(storageDto));
  }

  @Test
  void shouldPartialDeleteFileFromS3() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse);
    final ResponseInputStream<GetObjectResponse> s3Object = createObject(jsonFileContent);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(s3Object);
    when(s3Mock.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName)
        .build())).thenReturn(getBucketVersioningResponse);
    when(
        s3Mock.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).prefix(key)
            .build())).thenReturn(versions);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture(), requestBodyCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.bucket(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.key(), is(key));

    final var resultInputStream = requestBodyCaptor.getValue().contentStreamProvider().newStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is(LifecycleState.DELETED.name()));

    final var resultUserMetadata = putObjectRequest.metadata();
    headObjectResponse.metadata().entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("1")
            .build());
    verify(s3Mock).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("2")
            .build());
    verify(s3Mock).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("3")
            .build());

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.PARTIAL));
  }

  @Test
  void shouldThrowExceptionWhenFailToPartialDeleteFile() {
    final StorageDto storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();
    final ResponseInputStream<GetObjectResponse> s3Object = createObject(jsonFileContent);

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(s3Object);

    when(s3Mock.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(
        AwsServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.delete(storageDto));
  }

  @Test
  void shouldNotUpdateFileContentIfPartialDeleteFileTypeNotJson() throws IOException {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key).build();

    var metadata = new HashMap<>(headObjectResponse.metadata());
    metadata.put("type", "doc");

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse.toBuilder().metadata(metadata).build());
    final ResponseInputStream<GetObjectResponse> s3Object = createObject(jsonFileContent);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(s3Object);
    when(s3Mock.getBucketVersioning(
        GetBucketVersioningRequest.builder().bucket(bucketName).build())).thenReturn(
        getBucketVersioningResponse);
    when(
        s3Mock.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).prefix(key)
            .build())).thenReturn(versions);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture(), requestBodyCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.bucket(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.key(), is(key));

    final var resultInputStream = requestBodyCaptor.getValue().contentStreamProvider().newStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is("SUBMITTED"));
    assertThat("Unexpected input stream.", resultJsonMap.get("forename"), is("forename"));

    final var resultUserMetadata = putObjectRequest.metadata();
    metadata.entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("1")
            .build());
    verify(s3Mock).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("2")
            .build());
    verify(s3Mock).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("3")
            .build());

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

    getBucketVersioningResponse = GetBucketVersioningResponse.builder().build();

    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(headObjectResponse);
    final ResponseInputStream<GetObjectResponse> s3Object = createObject(jsonFileContent);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName).key(key)
        .build())).thenReturn(s3Object);
    when(s3Mock.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName)
        .build()))
        .thenReturn(getBucketVersioningResponse);

    awsStorageService.delete(storageDto);

    verify(s3Mock).putObject(putRequestCaptor.capture(), requestBodyCaptor.capture());
    PutObjectRequest putObjectRequest = putRequestCaptor.getValue();

    assertThat("Unexpected bucket.", putObjectRequest.bucket(), is(bucketName));
    assertThat("Unexpected key.", putObjectRequest.key(), is(key));

    final var resultInputStream = requestBodyCaptor.getValue().contentStreamProvider().newStream();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
    assertThat("Unexpected input stream.", resultJsonMap.get("id"), is("1"));
    assertThat("Unexpected input stream.", resultJsonMap.get("lifecycleState"),
        is(LifecycleState.DELETED.name()));

    final var resultUserMetadata = putObjectRequest.metadata();
    headObjectResponse.metadata().entrySet().stream()
        .filter(meta -> meta.getKey() != "lifecyclestate")
        .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
    assertThat("Unexpected lifecycleState in object Metadata.",
        resultUserMetadata.get("lifecyclestate"), is(LifecycleState.DELETED.name()));

    verify(s3Mock, never()).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("1")
            .build());
    verify(s3Mock, never()).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("2")
            .build());
    verify(s3Mock, never()).deleteObject(
        DeleteObjectRequest.builder().bucket(bucketName).key(key).versionId("3")
            .build());

    verify(snsMock).publishSnsDeleteEventTopic(deleteEventCaptor.capture());
    DeleteEventDto resultDeleteEvent = deleteEventCaptor.getValue();
    assertThat("Unexpected bucket.", resultDeleteEvent.getBucket(), is(bucketName));
    assertThat("Unexpected key.", resultDeleteEvent.getKey(), is(key));
    assertThat("Unexpected delete type.",
        resultDeleteEvent.getDeleteType(), is(DeleteType.PARTIAL));
  }

  private ResponseInputStream<GetObjectResponse> createObject(String fileContent) {
    GetObjectResponse response = GetObjectResponse.builder().build();
    ByteArrayInputStream contentStream = new ByteArrayInputStream(fileContent.getBytes());

    return new ResponseInputStream<>(response, contentStream);
  }

  private S3Object createSummary(String objectKey) {
    return S3Object.builder().key(objectKey).build();
  }

  private void expectMetadataInteractions(String objectBucketName, String objectKey,
      HeadObjectResponse headObjectResp,
      String objectName, String objectType) {
    var metadata = new HashMap<>(headObjectResp.metadata());
    metadata.put("name", objectName);
    metadata.put("type", objectType);
    headObjectResp = headObjectResp.toBuilder().metadata(metadata).build();
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(objectBucketName).key(objectKey)
        .build())).thenReturn(headObjectResp);
  }

  @Nested
  class SortingTest {

    @Test
    void listFilesShouldSort() {
      final String key1 = key + "1";
      final String key2 = key + "2";
      final String key3 = key + "3";
      final String keyNullName = key + "nullName";
      S3Object s3Object1 = createSummary(key1);
      S3Object s3Object2 = createSummary(key2);
      S3Object s3Object3 = createSummary(key3);
      S3Object s3Object4 = createSummary(keyNullName);

      ListObjectsResponse listObjectsResponse = ListObjectsResponse.builder()
          .contents(List.of(s3Object2, s3Object4, s3Object1, s3Object3))
          .build();
      when(s3Mock.listObjects(
          ListObjectsRequest.builder().bucket(bucketName).prefix(folderName + "/")
              .build())).thenReturn(listObjectsResponse);
      final String test1Name = "test1.foo";
      final String test2Name = "test2.foo";
      final String test3Name = "test3.foo";
      expectMetadataInteractions(bucketName, key1, HeadObjectResponse.builder().build(), test1Name,
          null);
      expectMetadataInteractions(bucketName, key2, HeadObjectResponse.builder().build(), test2Name,
          null);
      expectMetadataInteractions(bucketName, key3, HeadObjectResponse.builder().build(), test3Name,
          null);
      expectMetadataInteractions(bucketName, keyNullName, HeadObjectResponse.builder().build(),
          null, null);

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
      final String key1 = key + "1";
      final String keyNullName = key + "nullName";
      S3Object s3Object1 = createSummary(key1);
      S3Object s3Object4 = createSummary(keyNullName);
      ListObjectsResponse listObjectsResponse = ListObjectsResponse.builder()
          .contents(List.of(s3Object4, s3Object1))
          .build();
      when(s3Mock.listObjects(
          ListObjectsRequest.builder().bucket(bucketName).prefix(folderName + "/")
              .build())).thenReturn(listObjectsResponse);
      expectMetadataInteractions(bucketName, key1, HeadObjectResponse.builder().build(), null,
          null);
      expectMetadataInteractions(bucketName, keyNullName, HeadObjectResponse.builder().build(),
          null, null);

      final StorageDto storageDto = StorageDto.builder().bucketName(bucketName)
          .folderPath(folderName).build();
      final var actualList = awsStorageService.listFiles(storageDto, false, sort);

      assertThat(actualList, hasSize(2));
    }
  }
}
