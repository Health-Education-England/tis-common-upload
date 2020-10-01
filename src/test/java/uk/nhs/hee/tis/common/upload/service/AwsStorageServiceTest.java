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
  private AmazonS3 amazonS3;

  @Mock
  private MultipartFile file1;

  @Mock
  private MultipartFile file2;

  @Mock
  private InputStream inputStream;

  @Mock
  private PutObjectResult result;

  @Mock
  private ObjectListing objectListing;

  @Mock
  private ObjectMetadata metadata;

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
        .files(List.of(file1, file2))
        .build();

    when(file1.getOriginalFilename()).thenReturn(fileName);
    when(file2.getOriginalFilename()).thenReturn(fileName);
    when(file1.getInputStream()).thenReturn(inputStream);
    when(file2.getInputStream()).thenReturn(inputStream);
    when(amazonS3.putObject(any())).thenReturn(result);
    final var putObjectResult = awsStorageService.upload(storageDto);
    assertThat(putObjectResult, hasSize(2));
  }

  @Test
  void shouldHandleExceptionIfUploadFails() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .files(List.of(file1))
        .build();

    when(amazonS3.putObject(any())).thenThrow(AmazonServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.upload(storageDto));
  }

  @Test
  void shouldDownloadFileFromS3() {
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
  void shouldThrowExceptionWhenDownloadFileNotFound() {
    final var storageDto = StorageDto.builder().bucketName(bucketName)
        .key(fileName).build();
    final var s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(amazonS3.getObject(bucketName, key)).thenThrow(AmazonServiceException.class);

    assertThrows(AwsStorageException.class, () -> awsStorageService.download(storageDto));
  }


  @Test
  void getDataShouldReturnExpectedData() {
    final StorageDto input = StorageDto.builder().bucketName(bucketName).key(key).build();
    final S3Object stubbedValue = new S3Object();
    stubbedValue.setBucketName(bucketName);
    stubbedValue.setKey(key);
    stubbedValue.setObjectContent(new ByteArrayInputStream(fileContent.getBytes()));
    when(amazonS3.getObject(bucketName, key)).thenReturn(stubbedValue);

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
    when(amazonS3.getObject(bucketName, key)).thenThrow(new AmazonServiceException(
        expectedMessage));

    Throwable actual = assertThrows(AwsStorageException.class, () -> awsStorageService.getData(storageDto));
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
    when(amazonS3.listObjects(bucketName, folderName + "/")).thenReturn(objectListing);
    when(objectListing.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    when(amazonS3.getObjectMetadata(bucketName, key)).thenReturn(metadata);
    when(metadata.getUserMetaDataOf("name")).thenReturn("test.txt");
    when(metadata.getUserMetaDataOf("type")).thenReturn("txt");
    when(metadata.getUserMetadata()).thenReturn(Map.of("destination", "unknown"));

    final var objectSummaries = awsStorageService.listFiles(storageDto, true);
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
    when(amazonS3.listObjects(bucketName, folderName + "/")).thenReturn(objectListing);
    when(objectListing.getObjectSummaries()).thenReturn(List.of(s3ObjectSummary));
    when(amazonS3.getObjectMetadata(bucketName, key)).thenReturn(metadata);
    when(metadata.getUserMetaDataOf("name")).thenReturn(null);
    when(metadata.getUserMetaDataOf("type")).thenReturn(null);

    final var objectSummaries = awsStorageService.listFiles(storageDto, false);
    assertThat(objectSummaries, hasSize(1));
    assertThat(objectSummaries.get(0).getBucketName(), is(bucketName));
    assertThat(objectSummaries.get(0).getKey(), is(key));
    assertThat(objectSummaries.get(0).getFileName(), is(nullValue()));
    assertThat(objectSummaries.get(0).getCustomMetadata(), is(nullValue()));
    verify(metadata, never()).getUserMetadata();
  }

  @Test
  void shouldThrowExceptionWhenListNotFound() {
    final var storageDto = StorageDto.builder()
        .bucketName(bucketName)
        .folderPath(folderName)
        .build();
    when(amazonS3.listObjects(bucketName, folderName + "/"))
        .thenThrow(AmazonServiceException.class);
    assertThrows(AwsStorageException.class, () -> awsStorageService.listFiles(storageDto, false));
  }

  @Test
  void shouldDeleteFileFromS3() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    awsStorageService.delete(storageDto);
    verify(amazonS3).deleteObject(bucketName, key);
  }

  @Test
  void shouldThrowExceptionWhenFailToDeleteFile() {
    final var storageDto = StorageDto.builder().bucketName(bucketName).key(key)
        .build();
    doThrow(AmazonServiceException.class).when(amazonS3).deleteObject(bucketName, key);
    assertThrows(AwsStorageException.class, () -> awsStorageService.delete(storageDto));
  }

}
