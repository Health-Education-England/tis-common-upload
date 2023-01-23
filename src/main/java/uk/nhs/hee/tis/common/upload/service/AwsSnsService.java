/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.common.upload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;

@Slf4j
@Service
public class AwsSnsService {

  private final SnsClient snsClient;

  private final String deleteEventTopicArn;
  private final ObjectMapper objectMapper;

  AwsSnsService(@Value("${cloud.aws.sns.delete-event-topic}") String deleteEventTopicArn,
      SnsClient snsClient,
      ObjectMapper objectMapper) {
    this.deleteEventTopicArn = deleteEventTopicArn;
    this.snsClient = snsClient;
    this.objectMapper = objectMapper;
  }

  public void publishSnsDeleteEventTopic(DeleteEventDto deleteEventDto) {
    try {
      JsonNode deleteEventJson = objectMapper.valueToTree(deleteEventDto);

      PublishRequest request = PublishRequest.builder()
          .message(deleteEventJson.toString())
          .topicArn(deleteEventTopicArn)
          .build();

      PublishResponse result = snsClient.publish(request);
      log.info("Delete event sent to SNS. Bucket: '{}'. Key: '{}'.",
          deleteEventDto.getBucket(), deleteEventDto.getKey());

    } catch (SnsException e) {
      log.error("Fail to send to SNS topic: {}", e.awsErrorDetails().errorMessage());
    }
  }
}
