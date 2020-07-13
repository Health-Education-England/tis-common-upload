package uk.nhs.hee.tis.common.upload.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileUploadDto {

  @NotBlank(message = "Bucket name cannot be empty")
  private String bucketName;
  @NotBlank(message = "Folder path cannot not be empty")
  private String folderPath;
  @NotNull(message = "File should not be empty")
  private MultipartFile file;

}
