package uk.nhs.hee.tis.common.upload.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.hee.tis.common.upload.service.AwsStorageService;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(AwsStorageController.class)
public class AwsStorageControllerTest {

  private static final String STORAGE_URL = "/api/storage";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AwsStorageService storageService;

  private String folderPath;
  private String bucketName;

  @BeforeEach
  public void setup() {
    folderPath = "1/concern";
    bucketName = "tis-test-bucket";
  }

  @Test
  public void shouldUploadFile() throws Exception {
    final var file = new MockMultipartFile("file", "test.txt",
        "text/plain", "Spring Framework".getBytes());

    this.mockMvc.perform(multipart(STORAGE_URL)
        .file(file)
        .param("bucketName", bucketName)
        .param("folderPath", folderPath))
        .andExpect(status().isOk());
  }
}
