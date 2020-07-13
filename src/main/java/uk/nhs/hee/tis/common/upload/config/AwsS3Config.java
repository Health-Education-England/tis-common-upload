package uk.nhs.hee.tis.common.upload.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AwsS3Config {

  @Value("${app.aws.access.key.id}")
  private String awsAccessKeyId;

  @Value("${app.aws.secret.access.key}")
  private String awsSecretAccessKey;

  @Bean
  public  AWSCredentialsProvider credentialsProvider() {
    final var basic = new BasicAWSCredentials(this.awsAccessKeyId, this.awsSecretAccessKey);
    return new AWSStaticCredentialsProvider(basic);
  }

  @Bean
  public AmazonS3 amazonS3() {
    return AmazonS3ClientBuilder.standard().withCredentials(credentialsProvider()).build();
  }
}
