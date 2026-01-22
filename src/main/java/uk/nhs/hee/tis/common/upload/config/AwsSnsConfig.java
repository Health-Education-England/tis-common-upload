package uk.nhs.hee.tis.common.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * A configuration class for AWS SNS client.
 */
@Configuration
public class AwsSnsConfig {

  @Bean
  public SnsClient snsClient() {
    return SnsClient.create();
  }
}
