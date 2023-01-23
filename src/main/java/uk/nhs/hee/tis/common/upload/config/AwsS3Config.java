package uk.nhs.hee.tis.common.upload.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsS3Config {

  @Bean
  public AmazonS3 amazonS3(@Value("${cloud.aws.region.static}") String region) {
    return AmazonS3ClientBuilder.standard()
        .withRegion(region)
        .build();
  }
}
