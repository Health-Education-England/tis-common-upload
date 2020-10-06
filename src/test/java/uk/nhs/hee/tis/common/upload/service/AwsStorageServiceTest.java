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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.github.javafaker.Faker;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  private AmazonS3 s3Mock;

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

  private String fileName;
  private String bucketName;
  private String folderName;
  private String fileContent;
  private String key;

  @BeforeEach
  void setup() {
    fileName = faker.lorem().characters(10);
    bucketName = faker.lorem().characters(10);
    folderName = faker.lorem().characters(10);
    fileContent = faker.lorem().sentence(5);
    key = faker.lorem().characters(10);
  }

  @Test
  void shouldUploadFile() throws IOException {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .files(List.of(file1Mock, file2Mock))
        .build();

    when(file1Mock.getOriginalFilename()).thenReturn(fileName);
    when(file2Mock.getOriginalFilename()).thenReturn(fileName);
    when(file1Mock.getInputStream()).thenReturn(inputStreamMock);
    when(file2Mock.getInputStream()).thenReturn(inputStreamMock);
    when(s3Mock.putObject(any())).thenReturn(resultMock);
    final var putObjectResult = awsStorageService.upload(storageDto);
    assertThat(putObjectResult, hasSize(2));
  }

  @Test
  void shouldHandleExceptionIfUploadFails() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .files(List.of(file1Mock))
        .build();

    when(s3Mock.putObject(any())).thenThrow(AmazonServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.upload(storageDto));
  }

  @Test
  void shouldDownloadFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(s3Mock.getObject(bucketName, key)).thenReturn(s3Object);
    final var byteArray = awsStorageService.download(storageDto);
    final var downloadedContent = new String(byteArray);

    assertThat(downloadedContent, is(fileContent));
  }

  @Test
  void shouldThrowExceptionWhenDownloadFileNotFound() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(fileName).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(s3Mock.getObject(bucketName, key)).thenThrow(AmazonServiceException.class);

    assertThrows(AwsStorageException.class, () -> awsStorageService.download(storageDto));
  }


  @Test
  void getDataShouldReturnExpectedData() {
    final StorageDto input = StorageDto.builder().bucketName(bucketName).key(key).build();
    final S3Object stubbedValue = new S3Object();
    stubbedValue.setBucketName(bucketName);
    stubbedValue.setKey(key);
    stubbedValue.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(s3Mock.getObject(bucketName, key)).thenReturn(stubbedValue);

    final String actual = awsStorageService.getData(input);

    assertEquals(fileContent, actual);
  }

  @Test
  void getDataShouldWrapException() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(key).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    String expectedMessage = "Expected Exception";
    when(s3Mock.getObject(bucketName, key)).thenThrow(new AmazonServiceException(
        expectedMessage));

    Throwable actual =
        assertThrows(AwsStorageException.class, () -> awsStorageService.getData(storageDto));
    assertThat(actual.getMessage(), startsWith(expectedMessage));
  }

  @Test
  void shouldListFilesFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var key = folderName + "/test.txt";
    final var s3ObjectSummary = new S3ObjectSummary();
    s3ObjectSummary.setBucketName(bucketName);
    s3ObjectSummary.setKey(key);
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(metadataMock);
    when(metadataMock.getUserMetaDataOf("name")).thenReturn("test.txt");
    when(metadataMock.getUserMetaDataOf("type")).thenReturn("txt");
    when(metadataMock.getUserMetadata()).thenReturn(Map.of("destination", "unknown"));

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
    final var storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var key = folderName + "/test.txt";
    final var s3ObjectSummary = new S3ObjectSummary();
    s3ObjectSummary.setBucketName(bucketName);
    s3ObjectSummary.setKey(key);
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    when(s3Mock.getObjectMetadata(bucketName, key)).thenReturn(metadataMock);
    when(metadataMock.getUserMetaDataOf("name")).thenReturn(null);
    when(metadataMock.getUserMetaDataOf("type")).thenReturn(null);

    final var objectSummaries = awsStorageService.listFiles(storageDto, false, null);
    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
    assertThat(objectSummaries.get(0).getFileName(), is(nullValue()));
    assertThat(objectSummaries.get(0).getCustomMetadata(), is(nullValue()));
    verify(metadataMock, never()).getUserMetadata();
  }

  @Test
  void listFilesShouldSort() {
    S3ObjectSummary s3Object1 = createSummary(bucketName, key + "1");
    S3ObjectSummary s3Object2 = createSummary(bucketName, key + "2");
    S3ObjectSummary s3Object3 = createSummary(bucketName, key + "3");
    S3ObjectSummary s3Object4 = createSummary(bucketName, key + "nullName");
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(
        Arrays.asList(s3Object2, s3Object4, s3Object1, s3Object3));
    when(s3Mock.getObjectMetadata(bucketName, key + "1")).thenReturn(metadataMock);
    when(s3Mock.getObjectMetadata(bucketName, key + "2")).thenReturn(metadataMock2);
    when(s3Mock.getObjectMetadata(bucketName, key + "3")).thenReturn(metadataMock3);
    when(s3Mock.getObjectMetadata(bucketName, key + "nullName")).thenReturn(metadataMock4);
    String test1Name = "test1.foo";
    when(metadataMock.getUserMetaDataOf("name")).thenReturn(test1Name);
    when(metadataMock.getUserMetaDataOf("type")).thenReturn(null);
    String test2Name = "test2.foo";
    when(metadataMock2.getUserMetaDataOf("name")).thenReturn(test2Name);
    when(metadataMock2.getUserMetaDataOf("type")).thenReturn(null);
    String test3Name = "test3.foo";
    when(metadataMock3.getUserMetaDataOf("name")).thenReturn(test3Name);
    when(metadataMock3.getUserMetaDataOf("type")).thenReturn(null);
    when(metadataMock4.getUserMetaDataOf("name")).thenReturn(null);
    when(metadataMock4.getUserMetaDataOf("type")).thenReturn(null);

    final StorageDto storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();
    final var fileSummaryDtoList = awsStorageService.listFiles(storageDto, false, "fileName,desc");
    assertThat(fileSummaryDtoList, hasSize(4));
    assertThat(fileSummaryDtoList.get(0).getFileName(), is(test3Name));
    assertThat(fileSummaryDtoList.get(1).getFileName(), is(test2Name));
    assertThat(fileSummaryDtoList.get(2).getFileName(), is(test1Name));
    assertThat(fileSummaryDtoList.get(3).getFileName(), nullValue());
  }

  @Test
  void listFilesWithNoPropertyShouldStillReturn() {
    S3ObjectSummary s3Object1 = createSummary(bucketName, key + "1");
    S3ObjectSummary s3Object2 = createSummary(bucketName, key + "2");
    S3ObjectSummary s3Object3 = createSummary(bucketName, key + "3");
    S3ObjectSummary s3Object4 = createSummary(bucketName, key + "nullName");
    when(s3Mock.listObjects(bucketName, folderName + "/")).thenReturn(objectListingMock);
    when(objectListingMock.getObjectSummaries()).thenReturn(
        Arrays.asList(s3Object2, s3Object4, s3Object1, s3Object3));
    when(s3Mock.getObjectMetadata(bucketName, key + "1")).thenReturn(metadataMock);
    when(s3Mock.getObjectMetadata(bucketName, key + "2")).thenReturn(metadataMock2);
    when(s3Mock.getObjectMetadata(bucketName, key + "3")).thenReturn(metadataMock3);
    when(s3Mock.getObjectMetadata(bucketName, key + "nullName")).thenReturn(metadataMock4);
    String test1Name = "test1.foo";
    when(metadataMock.getUserMetaDataOf("name")).thenReturn(test1Name);
    when(metadataMock.getUserMetaDataOf("type")).thenReturn(null);
    String test2Name = "test2.foo";
    when(metadataMock2.getUserMetaDataOf("name")).thenReturn(test2Name);
    when(metadataMock2.getUserMetaDataOf("type")).thenReturn(null);
    String test3Name = "test3.foo";
    when(metadataMock3.getUserMetaDataOf("name")).thenReturn(test3Name);
    when(metadataMock3.getUserMetaDataOf("type")).thenReturn(null);
    when(metadataMock4.getUserMetaDataOf("name")).thenReturn(null);
    when(metadataMock4.getUserMetaDataOf("type")).thenReturn(null);
    final StorageDto storageDto = StorageDto.builder().bucketName(bucketName).folderPath(folderName)
        .build();

    final var fileSummaryDtoList = awsStorageService.listFiles(storageDto, false, "nonexistantProp,asc");

    assertThat(fileSummaryDtoList, hasSize(4));
  }

  private S3ObjectSummary createSummary(String bucketName, String objectKey) {
    S3ObjectSummary summary = new S3ObjectSummary();
    summary.setBucketName(bucketName);
    summary.setKey(objectKey);
    return summary;
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
  }

  @Test
  void shouldThrowExceptionWhenFailToDeleteFile() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    doThrow(AmazonServiceException.class).when(s3Mock).deleteObject(bucketName, key);
    assertThrows(AwsStorageException.class, () -> awsStorageService.delete(storageDto));
  }

}
