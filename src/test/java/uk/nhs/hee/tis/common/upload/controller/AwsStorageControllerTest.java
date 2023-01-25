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

package uk.nhs.hee.tis.common.upload.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.hee.tis.common.upload.dto.FileSummaryDto;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

@WebMvcTest(AwsStorageController.class)
class AwsStorageControllerTest {

  private static final String STORAGE_URL = "/api/storage";
  private static final String UPLOAD = "/upload";
  private static final String DOWNLOAD = "/download";
  private static final String LIST = "/list";
  private static final String DELETE = "/delete";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AwsStorageService storageServiceMock;

  @Captor
  private ArgumentCaptor<StorageDto> storageDtoCaptor;

  private String folderPath;
  private String bucketName;
  private String key;
  private String metadataFileName;
  private String metadataFileType;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setup() {
    folderPath = "1/concern";
    bucketName = "tis-test-bucket";
    String fileName = "test.txt";
    key = folderPath + "/" + fileName;
    metadataFileName = fileName;
    metadataFileType = "txt";
  }

  @Test
  void shouldUploadFile() throws Exception {
    final var file = new MockMultipartFile("files", "test.txt",
        "text/plain", "Spring Framework".getBytes());

    mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
            .file(file)
            .param("bucketName", bucketName)
            .param("folderPath", folderPath)
            .param("customMetadata[key]", "value")
            .param("customMetadata[uploadDate]", "1992-08-07"))
        .andExpect(status().isOk());

    verify(storageServiceMock).upload(storageDtoCaptor.capture());
    StorageDto expectedDto = StorageDto.builder().bucketName(bucketName).folderPath(folderPath)
        .customMetadata(Map.of("key", "value", "uploadDate", "1992-08-07"))
        .files(List.of(file)).build();
    assertThat(storageDtoCaptor.getValue(), equalTo(expectedDto));
  }

  @Test
  void shouldUploadMultipleFiles() throws Exception {
    final var file1 = new MockMultipartFile("files", "test1.txt",
        "text/plain", "Spring Framework".getBytes());
    final var file2 = new MockMultipartFile("files", "test2.txt",
        "text/plain", "Spring Framework".getBytes());

    mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
            .file(file1)
            .file(file2)
            .param("bucketName", bucketName)
            .param("folderPath", folderPath))
        .andExpect(status().isOk());
  }

  @Test
  void uploadFileShouldThrowExceptionWhenNoBucketNameProvided() throws Exception {
    final var file = new MockMultipartFile("files", "test.txt",
        "text/plain", "Spring Framework".getBytes());

    mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
            .file(file)
            .param("folderPath", folderPath))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void shouldDownloadFile() throws Exception {
    final var content = "This is test file";
    when(storageServiceMock.download(any())).thenReturn(content.getBytes());
    mockMvc.perform(get(STORAGE_URL + DOWNLOAD)
            .param("bucketName", bucketName)
            .param("key", key))
        .andExpect(status().isOk())
        .andExpect(content().string(content));
  }

  @Test
  void downloadFileShouldThrowExceptionWhenNoKeyProvided() throws Exception {
    when(storageServiceMock.download(any())).thenReturn("test".getBytes());
    mockMvc.perform(get(STORAGE_URL + DOWNLOAD)
            .param("bucketName", bucketName))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void getDataShouldRespondWithExpectedData() throws Exception {
    when(storageServiceMock.getData(storageDtoCaptor.capture())).thenReturn(
        "{\"table\": \"Concern\",  \"data\": {\"id\": 40,\"name\": \"Dolore, Harold\"}}");
    mockMvc.perform(get(STORAGE_URL + "/data")
            .param("bucketName", bucketName)
            .param("key", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.table", equalTo("Concern")))
        .andExpect(jsonPath("$.data.id", equalTo(40)))
        .andExpect(jsonPath("$.data.name", equalTo("Dolore, Harold")));
    var actualStorageDto = storageDtoCaptor.getValue();
    assertEquals(bucketName, actualStorageDto.getBucketName());
    assertEquals(key, actualStorageDto.getKey());
  }

  @Test
  void getDataShouldRespond4xxOnStorageException() throws Exception {
    when(storageServiceMock.getData(storageDtoCaptor.capture()))
        .thenThrow(new AwsStorageException("Storage Exception"));

    mockMvc.perform(get(STORAGE_URL + "/data")
            .param("bucketName", bucketName)
            .param("key", key))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void shouldListAllFiles() throws Exception {
    Map<String, String> defaultMetadataMap = Map.of("key", "value");
    final var fileSummaryDto = FileSummaryDto.builder().bucketName(bucketName).key(key)
        .customMetadata(defaultMetadataMap)
        .fileName(metadataFileName).fileType(metadataFileType).build();
    final var fileSummaryDtoList = List.of(fileSummaryDto);
    when(storageServiceMock.listFiles(any(), eq(false), eq("interest.score,desc")))
        .thenReturn(fileSummaryDtoList);
    mockMvc.perform(get(STORAGE_URL + LIST)
            .param("bucketName", bucketName)
            .param("folderPath", folderPath)
            .param("sort", "interest.score,desc"))
        .andExpect(status().isOk())
        .andExpect(content().string(objectMapper.writeValueAsString(fileSummaryDtoList)));
  }

  @Test
  void listAllFilesShouldThrowExceptionWhenFolderPathNotProvided() throws Exception {
    mockMvc.perform(get(STORAGE_URL + LIST)
            .param("bucketName", bucketName))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void shouldDeleteFile() throws Exception {
    final String content = "[" + key + "] deleted successfully.";
    mockMvc.perform(delete(STORAGE_URL + DELETE)
            .param("bucketName", bucketName)
            .param("key", key))
        .andExpect(status().isOk())
        .andExpect(content().string(content));
  }

  @Test
  void deleteShouldRespond4xxOnException() throws Exception {
    doThrow(new AwsStorageException("Storage Exception"))
        .when(storageServiceMock).delete(storageDtoCaptor.capture());
    mockMvc.perform(delete(STORAGE_URL + DELETE)
            .param("bucketName", bucketName)
            .param("key", key))
        .andExpect(status().is4xxClientError());
    StorageDto expected = StorageDto.builder().bucketName(bucketName).key(key).build();
    assertEquals(expected, storageDtoCaptor.getValue());
  }

  @Test
  void deleteShouldThrowExceptionWhenKeyIsMissing() throws Exception {
    mockMvc.perform(get(STORAGE_URL + DELETE)
            .param("bucketName", bucketName))
        .andExpect(status().is4xxClientError());
    verifyNoInteractions(storageServiceMock);
  }
}
