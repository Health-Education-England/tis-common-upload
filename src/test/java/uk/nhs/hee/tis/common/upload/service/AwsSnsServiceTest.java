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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;
import uk.nhs.hee.tis.common.upload.enumeration.DeleteType;

class AwsSnsServiceTest {

  private static final String TOPIC_ARN = "arn:aws:sns:eu-west-2:0000000:topic-arn";

  private AwsSnsService awsSnsService;

  private AmazonSNS snsClientMock;

  @BeforeEach
  void setup() {
    snsClientMock = mock(AmazonSNS.class);
    awsSnsService = new AwsSnsService(TOPIC_ARN, snsClientMock, new ObjectMapper());
  }

  @Test
  void shouldPublishDeleteEventToSnsTopic() {
    DeleteEventDto deleteEventDto = new DeleteEventDto();
    deleteEventDto.setBucket("bucket-name");
    deleteEventDto.setKey("file-to-delete.json");
    deleteEventDto.setDeleteType(DeleteType.PARTIAL);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode deleteEventJson = objectMapper.valueToTree(deleteEventDto);

    awsSnsService.publishSnsDeleteEventTopic(deleteEventDto);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClientMock).publish(requestCaptor.capture());

    PublishRequest resultPubRequest = requestCaptor.getValue();
    assertThat("Unexpected message.", resultPubRequest.getMessage(),
        is(deleteEventJson.toString()));
    assertThat("Unexpected topic Arn.", resultPubRequest.getTopicArn(), is(TOPIC_ARN));
  }
}
