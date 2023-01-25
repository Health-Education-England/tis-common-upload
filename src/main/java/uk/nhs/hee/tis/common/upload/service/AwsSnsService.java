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

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;

/**
 * A service providing access to SNS functionality.
 */
@Slf4j
@Service
public class AwsSnsService {

  private final AmazonSNS snsClient;

  private final String deleteEventTopicArn;
  private final ObjectMapper objectMapper;

  /**
   * Create a service providing access to SNS functionality.
   *
   * @param deleteEventTopicArn The topic arn to publish to.
   * @param snsClient           The SNS client to publish with.
   * @param objectMapper        The mapper to convert delete events to SNS messages.
   */
  AwsSnsService(@Value("${cloud.aws.sns.delete-event-topic}") String deleteEventTopicArn,
      AmazonSNS snsClient, ObjectMapper objectMapper) {
    this.deleteEventTopicArn = deleteEventTopicArn;
    this.snsClient = snsClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Publish delete message to SNS.
   *
   * @param deleteEventDto The delete event to publish.
   */
  public void publishSnsDeleteEventTopic(DeleteEventDto deleteEventDto) {
      JsonNode deleteEventJson = objectMapper.valueToTree(deleteEventDto);

      PublishRequest request = new PublishRequest()
          .withMessage(deleteEventJson.toString())
          .withTopicArn(deleteEventTopicArn);

    try {
      snsClient.publish(request);
      log.info("Delete event sent to SNS. Bucket: '{}'. Key: '{}'.",
          deleteEventDto.getBucket(), deleteEventDto.getKey());
    } catch (AmazonSNSException e) {
      String message = String.format("Failed to send to SNS topic '%s'", deleteEventTopicArn);
      log.error(message, e);
    }
  }
}
