package uk.nhs.hee.tis.common.upload.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A configuration class for Amazon S3 integration.
 */
@Configuration
public class AwsS3Config {

  @Bean
  public AmazonS3 amazonS3() {
    return AmazonS3ClientBuilder.defaultClient();
  }
}
