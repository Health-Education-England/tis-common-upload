package uk.nhs.hee.tis.common.upload.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsS3Config {

  @Value("${application.aws.sqs.regionCode}")
  private String awsRegionCode;
  @Bean
  public AmazonS3 amazonS3() {
    return AmazonS3ClientBuilder.standard().withRegion(awsRegionCode).build();
  }
}
