package uk.nhs.hee.tis.common.upload.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.hee.tis.common.upload.dto.FileSummaryDto;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(AwsStorageController.class)
public class AwsStorageControllerTest {

  private static final String STORAGE_URL = "/api/storage";
  private static final String UPLOAD = "/upload";
  private static final String DOWNLOAD = "/download";
  private static final String LIST = "/list";
  private static final String DELETE = "/delete";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AwsStorageService storageService;

  private String folderPath;
  private String bucketName;
  private String key;
  private String fileName;
  private String metadataFileName;
  private String metadataFileType;
  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  public void setup() {
    folderPath = "1/concern";
    bucketName = "tis-test-bucket";
    fileName = "test.txt";
    key = folderPath + "/" + fileName;
    metadataFileName = fileName;
    metadataFileType = "txt";
  }

  @Test
  public void shouldUploadFile() throws Exception {
    final var file = new MockMultipartFile("files", "test.txt",
        "text/plain", "Spring Framework".getBytes());

    this.mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
        .file(file)
        .param("bucketName", bucketName)
        .param("folderPath", folderPath))
        .andExpect(status().isOk());
  }

  @Test
  public void shouldUploadMultipleFiles() throws Exception {
    final var file1 = new MockMultipartFile("files", "test1.txt",
        "text/plain", "Spring Framework".getBytes());
    final var file2 = new MockMultipartFile("files", "test2.txt",
        "text/plain", "Spring Framework".getBytes());

    this.mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
        .file(file1)
        .file(file2)
        .param("bucketName", bucketName)
        .param("folderPath", folderPath))
        .andExpect(status().isOk());
  }

  @Test
  public void uploadFileShouldThrowExceptionWhenNoBucketNameProvided() throws Exception {
    final var file = new MockMultipartFile("files", "test.txt",
        "text/plain", "Spring Framework".getBytes());

    this.mockMvc.perform(multipart(STORAGE_URL + UPLOAD)
        .file(file)
        .param("folderPath", folderPath))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void shouldDownloadFile() throws Exception {
    final var content = "This is test file";
    when(storageService.download(any())).thenReturn(content.getBytes());
    this.mockMvc.perform(get(STORAGE_URL + DOWNLOAD)
        .param("bucketName", "test-bucket")
        .param("key", "1/concern/test.txt"))
        .andExpect(status().isOk())
        .andExpect(content().string(content));
  }

  @Test
  public void downloadFileShouldThrowExceptionWhenNoKeyProvided() throws Exception {
    when(storageService.download(any())).thenReturn("test".getBytes());
    this.mockMvc.perform(get(STORAGE_URL + DOWNLOAD)
        .param("bucketName", "test-bucket"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void shouldListAllFiles() throws Exception {
    final var fileSummaryDto = FileSummaryDto.builder().bucketName(bucketName).key(key)
        .fileName(metadataFileName).fileType(metadataFileType).build();
    final var fileSummaryDtoList = List.of(fileSummaryDto);
    when(storageService.listFiles(any())).thenReturn(fileSummaryDtoList);
    this.mockMvc.perform(get(STORAGE_URL + LIST)
        .param("bucketName", "test-bucket")
        .param("folderPath", "1/concern"))
        .andExpect(status().isOk())
        .andExpect(content().string(objectMapper.writeValueAsString(fileSummaryDtoList)));
  }

  @Test
  public void listAllFilesShouldThrowExceptionWhenFolderPathNotProvided() throws Exception {
    this.mockMvc.perform(get(STORAGE_URL + LIST)
        .param("bucketName", "test-bucket"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void shouldDeleteFile() throws Exception {
    final var key = "1/concern/test.txt";
    final String content = "[" + key + "] deleted successfully.";
    this.mockMvc.perform(delete(STORAGE_URL + DELETE)
        .param("bucketName", "test-bucket")
        .param("key", "1/concern/test.txt"))
        .andExpect(status().isOk())
        .andExpect(content().string(content));
  }

  @Test
  public void deleteShouldThrowExceptionWhenKeyIsMissing() throws Exception {
    this.mockMvc.perform(get(STORAGE_URL + DELETE)
        .param("bucketName", "test-bucket"))
        .andExpect(status().is4xxClientError());
  }
}
