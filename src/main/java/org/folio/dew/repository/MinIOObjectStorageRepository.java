package org.folio.dew.repository;

import io.minio.*;
import io.minio.errors.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MinIOObjectStorageRepository {

  private final MinioClient minioClient;

  public MinIOObjectStorageRepository(@Value("${minio.url}") String url, @Value("${minio.accessKey}") String accessKey,
      @Value("${minio.secretKey}") String secretKey) {
    this.minioClient = MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();
  }

  public ObjectWriteResponse uploadObject(String bucketName, String objectName, String filePath, String contentType)
      throws IOException, ServerException, InsufficientDataException, InternalException, InvalidResponseException,
      InvalidKeyException, NoSuchAlgorithmException, XmlParserException, ErrorResponseException {
    UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
        .bucket(bucketName)
        .object(objectName)
        .filename(filePath)
        .contentType(contentType)
        .build();

    return this.minioClient.uploadObject(uploadObjectArgs);
  }

  public ObjectWriteResponse composeObject(String bucketName, String destObjectName, List<String> sourceObjectNames)
      throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException,
      ServerException, InternalException, XmlParserException, ErrorResponseException {
    List<ComposeSource> sourceObjects = new ArrayList<>();

    for (String name : sourceObjectNames) {
      ComposeSource composeSource = ComposeSource.builder().bucket(bucketName).object(name).build();
      sourceObjects.add(composeSource);
    }

    ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder()
        .bucket(bucketName)
        .object(destObjectName)
        .sources(sourceObjects)
        .build();

    return minioClient.composeObject(composeObjectArgs);
  }

}
