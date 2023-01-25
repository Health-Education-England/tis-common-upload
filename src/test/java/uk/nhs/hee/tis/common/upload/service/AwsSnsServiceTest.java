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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;
import uk.nhs.hee.tis.common.upload.enumeration.DeleteType;

@SpringBootTest
@TestPropertySource(properties = {"cloud.aws.region.static=eu-west-2"})
class AwsSnsServiceTest {

  private static final String topicArn = "arn:aws:sns:eu-west-2:0000000:topic-arn";

  private final Faker faker = new Faker();

  private AwsSnsService awsSnsService;

  private SnsClient snsClientMock;

  @Captor
  ArgumentCaptor<PublishRequest> publishRequestCaptor;

  private String bucketName;
  private String key;
  private DeleteEventDto deleteEventDto;
  private JsonNode deleteEventJson;

  @BeforeEach
  void setup() {

    snsClientMock = mock(SnsClient.class);
    awsSnsService = new AwsSnsService(topicArn, snsClientMock, new ObjectMapper());

    ReflectionTestUtils.setField(
        awsSnsService, "deleteEventTopicArn", topicArn
    );

    bucketName = faker.lorem().characters(10);
    key = faker.lorem().characters(10);

    deleteEventDto = new DeleteEventDto();
    deleteEventDto.setBucket(bucketName);
    deleteEventDto.setKey(key);
    deleteEventDto.setDeleteType(DeleteType.PARTIAL);

    ObjectMapper objectMapper = new ObjectMapper();
    deleteEventJson = objectMapper.valueToTree(deleteEventDto);

  }

  @Test
  void shouldPublishDeleteEventToSnsTopic() {

    awsSnsService.publishSnsDeleteEventTopic(deleteEventDto);

    verify(snsClientMock).publish(publishRequestCaptor.capture());
    PublishRequest resultPubRequest = publishRequestCaptor.getValue();
    assertThat("Unexpected message.", resultPubRequest.message(), is(deleteEventJson.toString()));
    assertThat("Unexpected topic Arn.", resultPubRequest.topicArn(), is(topicArn));
  }
}
