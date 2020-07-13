package uk.nhs.hee.tis.common.upload.exception;

public class AwsStorageException extends RuntimeException {

  public AwsStorageException(final String message) {
    super(message);
  }
}
