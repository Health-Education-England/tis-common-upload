package uk.nhs.hee.tis.common.upload.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.nhs.hee.tis.common.upload.dto.DeleteEventDto;
import uk.nhs.hee.tis.common.upload.dto.FileSummaryDto;
import uk.nhs.hee.tis.common.upload.dto.StorageDto;
import uk.nhs.hee.tis.common.upload.enumeration.DeleteType;
import uk.nhs.hee.tis.common.upload.enumeration.LifecycleState;
import uk.nhs.hee.tis.common.upload.exception.AwsStorageException;

/**
 * A service providing AWS storage functionality.
 */
@Slf4j
@Service
public class AwsStorageService {

  public static final String SORT_DELIM = ",";
  private static final String USER_METADATA_FILE_NAME = "name";
  private static final String USER_METADATA_FILE_TYPE = "type";
  private static final String USER_METADATA_DELETE_TYPE = "deletetype";
  private static final String USER_METADATA_FIXED_FIELDS = "fixedfields";
  private static final String USER_METADATA_LIFE_CYCLE_STATE = "lifecyclestate";
  private static final String OBJECT_CONTENT_LIFE_CYCLE_STATE = "lifecycleState";
  private final S3Client amazonS3;
  private final AwsSnsService awsSnsService;
  private final ObjectMapper objectMapper;

  AwsStorageService(S3Client amazonS3, AwsSnsService awsSnsService, ObjectMapper objectMapper) {
    this.amazonS3 = amazonS3;
    this.awsSnsService = awsSnsService;
    this.objectMapper = objectMapper;
  }

  private static String getStringProperty(final FileSummaryDto o, final String name) {
    try {
      return (String) PropertyUtils.getNestedProperty(o, name);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      log.warn("Couldn't find the sort property [{}] on FileSummary:{}", name, o, e);
      return null;
    }
  }

  /**
   * Upload files in the bucket with a prefix of folderPath, specified in {@code storageDto}.
   *
   * @param storageDto representation of files to be uploaded to S3
   * @return result of attempts to store the objects
   */
  public List<PutObjectResponse> upload(final StorageDto storageDto) {
    final var bucketName = storageDto.getBucketName();
    final var folderPath = storageDto.getFolderPath();
    final var files = storageDto.getFiles();
    final var customMetadata = storageDto.getCustomMetadata();

    createBucketIfNotExist(bucketName);

    return files.stream().map(file -> {
      try {
        final var key = format("%s/%s", folderPath, file.getOriginalFilename());

        final HeadObjectResponse head = amazonS3
            .headObject(HeadObjectRequest.builder().bucket(bucketName).key(key)
                .build());
        Map<String, String> metadata = new HashMap<>(head.metadata());
        if (customMetadata != null) {
          metadata.putAll(customMetadata);
        }
        metadata.put(USER_METADATA_FILE_NAME, file.getOriginalFilename());
        metadata.put(
            USER_METADATA_FILE_TYPE,
            getExtension(file.getOriginalFilename())
        );

        PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(key)
            .metadata(metadata).contentLength(file.getSize()).build();

        log.info("uploading file: {} to bucket: {} with key: {}", file.getName(), bucketName, key);
        return amazonS3.putObject(request,
            RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

      } catch (Exception e) {
        log.error("Failed to upload file: {} in bucket: {}", file.getOriginalFilename(), bucketName,
            e);
        throw new AwsStorageException(e.getMessage());
      }
    }).toList();
  }

  /**
   * Get the object contents as bytes.
   *
   * @param storageDto holder for the bucket and object key
   * @return byte array of the object content
   */
  public byte[] download(final StorageDto storageDto) {
    try {
      log.info("Download file: {} from bucket: {} with key: {}", storageDto.getKey(),
          storageDto.getBucketName(), storageDto.getKey());
      GetObjectRequest request = GetObjectRequest.builder().bucket(storageDto.getBucketName())
          .key(storageDto.getKey()).build();

      try (ResponseInputStream<GetObjectResponse> s3Object = amazonS3.getObject(request)) {
        byte[] content = s3Object.readAllBytes();
        log.info("File downloaded successfully.");
        return content;
      }
    } catch (Exception e) {
      log.error("Fail to download file: {} from bucket: {}", storageDto.getKey(),
          storageDto.getBucketName(), e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * Get the object contents as a string.
   *
   * @param storageDto holder for the bucket and object key
   * @return text string of the object content
   */
  public String getData(StorageDto storageDto) {
    try (ResponseInputStream<GetObjectResponse> object = amazonS3.getObject(
        GetObjectRequest.builder().bucket(storageDto.getBucketName()).key(storageDto.getKey())
            .build())) {
      return IOUtils.toString(object, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Unable to retrieve object from S3 as a String", e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * List objects in a bucket under a given prefix.
   *
   * @param storageDto      holder for the bucket and folderPath (key prefix)
   * @param includeMetadata whether all custom metadata should be included
   * @param sort            A sort key and direction, See <a
   *                        href="https://docs.spring.io/spring-data/rest/docs/current/reference/html/#paging-and-sorting.sorting">...</a>
   * @return a list of summaries for objects which were found
   */
  public List<FileSummaryDto> listFiles(final StorageDto storageDto,
      final boolean includeMetadata, final String sort) {
    String bucketName = storageDto.getBucketName();
    try {
      final var listObjects = amazonS3
          .listObjects(ListObjectsRequest.builder().bucket(bucketName)
              .prefix(storageDto.getFolderPath() + "/")
              .build());
      var fileSummaryList = listObjects.contents().stream()
          .map(summary -> buildFileSummary(summary, bucketName, includeMetadata)).collect(toList());

      if (StringUtils.isNotBlank(sort) && sort.split(SORT_DELIM).length < 3) {
        String[] sortEntry = sort.split(SORT_DELIM);
        String sortKey = sortEntry[0];
        String sortDirection = sortEntry[1];

        Function<FileSummaryDto, String> extractor = o -> getStringProperty(o, sortKey);
        Comparator<String> comparator = Objects.equals(sortDirection, "desc")
            ? Comparator.reverseOrder() : Comparator.naturalOrder();

        fileSummaryList.sort(Comparator.comparing(extractor, Comparator.nullsLast(comparator)));
      }
      return fileSummaryList;
    } catch (Exception e) {
      log.error("Fail to list files from bucket: {} with folderPath: {}",
          bucketName, storageDto.getFolderPath(), e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * Delete the object identified by a key in a bucket.
   * The type of delete is determined by the USER_METADATA_DELETE_TYPE field in object metadata.
   * Partial delete will occur if USER_METADATA_DELETE_TYPE is set to DeleteType.PARTIAL,
   * otherwise, hard delete will perform by default.
   *
   * @param storageDto holder for the bucket and object key
   * @throws AwsStorageException if there is a problem deleting the object
   */
  public void delete(final StorageDto storageDto) {
    final HeadObjectResponse head = amazonS3
        .headObject(
            HeadObjectRequest.builder().bucket(storageDto.getBucketName()).key(storageDto.getKey())
                .build());
    Map<String, String> metadata = head.metadata();
    String metaDeleteType = metadata == null
        ? null
        : metadata.get(USER_METADATA_DELETE_TYPE);

    if (metaDeleteType != null && metaDeleteType.equals(DeleteType.PARTIAL.name())) {
      partialDelete(storageDto, metadata);
    } else {
      hardDelete(storageDto);
    }
  }

  /**
   * Delete the whole object from S3, and send delete event notification to SNS after deletion.
   *
   * @param storageDto holder for the bucket and object key
   * @throws AwsStorageException if there is a problem deleting the object
   */
  private void hardDelete(final StorageDto storageDto) {
    try {
      log.info("Remove file from bucket: {} with key: {}",
          storageDto.getBucketName(), storageDto.getKey());
      amazonS3.deleteObject(
          DeleteObjectRequest.builder().bucket(storageDto.getBucketName()).key(storageDto.getKey())
              .build());
      log.info("File is removed successfully.");

      DeleteEventDto deleteEventDto = DeleteEventDto.builder()
          .bucket(storageDto.getBucketName())
          .key(storageDto.getKey())
          .deleteType(DeleteType.HARD)
          .build();
      awsSnsService.publishSnsDeleteEventTopic(deleteEventDto);
    } catch (Exception e) {
      log.error("Fail to delete file from bucket {} with key {}: {}",
          storageDto.getBucketName(), storageDto.getKey(), e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  /**
   * Partial delete from object content, and send delete event notification to SNS after deletion.
   * For json file type object, the fields matched with metadata `x-amz-meta-fixedfields` will stay,
   * other fields will be removed from object content for the latest version.
   * Previous version will be deleted. Lifecycle state in user metadata will change to `DELETED`.
   *
   * @param storageDto holder for the bucket and object key
   * @param originalMetadata metadata of the object
   * @throws AwsStorageException if there is a problem deleting the object
   */
  private void partialDelete(final StorageDto storageDto, Map<String, String> originalMetadata) {
    try {
      final String bucket = storageDto.getBucketName();
      final String key = storageDto.getKey();

      log.info("Partial delete file from bucket: {} with key: {}", bucket, key);

      // Object Content
      final String[] fixedFields = originalMetadata.get(USER_METADATA_FIXED_FIELDS).split(",");
      String strOriginalContent = getData(storageDto);
      if (originalMetadata.get(USER_METADATA_FILE_TYPE).equals("json")) {
        JsonNode jsonNode = objectMapper.readTree(strOriginalContent);

        for (Iterator<String> fieldIterator = jsonNode.fieldNames(); fieldIterator.hasNext(); ) {
          String fieldName = fieldIterator.next();

          if (!Set.of(fixedFields).contains(fieldName)) {
            fieldIterator.remove();
          }
        }
        ((ObjectNode) jsonNode).put(OBJECT_CONTENT_LIFE_CYCLE_STATE,
            LifecycleState.DELETED.name());
        strOriginalContent = jsonNode.toString();
      }
      final var inputStream = new ByteArrayInputStream(strOriginalContent.getBytes());

      // Metadata
      Map<String, String> newMetadata = new HashMap<>(originalMetadata);
      newMetadata.put(USER_METADATA_LIFE_CYCLE_STATE, LifecycleState.DELETED.name());

      long contentLength = strOriginalContent.length();
      final var request = PutObjectRequest.builder().bucket(bucket).key(key).metadata(newMetadata)
          .contentLength(contentLength).build();
      amazonS3.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));

      deletePreviousVersions(bucket, key);

      log.info("Partial delete successfully.");

      DeleteEventDto deleteEventDto = DeleteEventDto.builder()
          .bucket(storageDto.getBucketName())
          .key(storageDto.getKey())
          .deleteType(DeleteType.PARTIAL)
          .fixedFields(fixedFields)
          .build();
      awsSnsService.publishSnsDeleteEventTopic(deleteEventDto);
    } catch (Exception e) {
      log.error("Fail to partial delete file from bucket {} with key {}: {}",
          storageDto.getBucketName(), storageDto.getKey(), e);
      throw new AwsStorageException(e.getMessage());
    }
  }

  private void deletePreviousVersions(final String bucket, final String key) {
    var status = amazonS3.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket)
        .build()).status();
    if (status != null && status.equals(BucketVersioningStatus.ENABLED)) {
      final ListObjectVersionsResponse versions = amazonS3.listObjectVersions(
          ListObjectVersionsRequest.builder().bucket(bucket).prefix(key)
              .build());
      for (ObjectVersion version : versions.versions()) {
        if (Boolean.FALSE.equals(version.isLatest())) {
          amazonS3.deleteObject(
              DeleteObjectRequest.builder().bucket(bucket).key(key).versionId(version.versionId())
                  .build());
        }
      }
    }
  }

  private void createBucketIfNotExist(final String bucketName) {
    try {
      amazonS3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      return;
    } catch (NoSuchBucketException e) {
      // expected
    }
    amazonS3.createBucket(CreateBucketRequest.builder().bucket(bucketName)
        .build());
  }

  private FileSummaryDto buildFileSummary(final S3Object summary, final String bucketName,
      final boolean includeCustomMetadata) {
    final var head = amazonS3
        .headObject(HeadObjectRequest.builder().bucket(bucketName).key(summary.key())
            .build());
    var metadata = head.metadata();
    log.debug("Metadata details for file:{}, Metadata: {}", summary.key(), metadata);
    return FileSummaryDto.builder()
        .bucketName(bucketName)
        .key(summary.key())
        .fileName(metadata.get(USER_METADATA_FILE_NAME))
        .fileType(metadata.get(USER_METADATA_FILE_TYPE))
        .customMetadata(includeCustomMetadata ? metadata : null)
        .build();
  }
}
