package uk.nhs.hee.tis.common.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * A configuration class for Amazon S3 integration.
 */
@Configuration
public class AwsS3Config {

  @Bean
  public S3Client amazonS3() {
    return S3Client.create();
  }
}
