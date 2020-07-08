package uk.nhs.hee.tis.common.upload.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AWSS3Config {

  @Value("${app.aws.access.key.id}")
  private String awsAccessKeyId;

  @Value("${app.aws.secret.access.key}")
  private String awsSecretAccessKey;

  @Bean
  AWSCredentialsProvider credentialsProvider() {
    log.info("AWS ACCESS KEY ID [{}]", this.awsAccessKeyId);
    final var secretFirst10 = StringUtils.isEmpty(this.awsSecretAccessKey) ? null : this.awsSecretAccessKey.substring(0, 10);
    log.info("AWS SECRET ACCESS KEY 1-10[{}]", secretFirst10);
    final var basic = new BasicAWSCredentials(this.awsAccessKeyId,
        this.awsSecretAccessKey);
    return new AWSStaticCredentialsProvider(basic);
  }
}
