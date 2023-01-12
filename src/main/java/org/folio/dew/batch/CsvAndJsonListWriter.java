package org.folio.dew.batch;

import org.folio.dew.domain.dto.Formatable;
import org.folio.dew.repository.S3CompatibleResource;
import org.folio.dew.repository.S3CompatibleStorage;

import java.util.List;
import java.util.stream.Collectors;

public class CsvAndJsonListWriter<O, T extends Formatable<O>, R extends S3CompatibleStorage> extends AbstractStorageStreamWriter<List<T>, R> {
  private final CsvAndJsonWriter<O, T, R> delegate;

  public CsvAndJsonListWriter(String tempOutputFilePath, String columnHeaders, String[] extractedFieldNames, FieldProcessor fieldProcessor, R storage) {
    super(tempOutputFilePath, columnHeaders, extractedFieldNames, fieldProcessor, storage);
    delegate = new CsvAndJsonWriter<>(tempOutputFilePath, columnHeaders, extractedFieldNames, fieldProcessor, storage);
    setResource(new S3CompatibleResource<>(tempOutputFilePath, storage));
  }

  @Override
  public void write(List<? extends List<T>> lists) throws Exception {
    delegate.write(lists.stream().flatMap(List::stream).collect(Collectors.toList()));
  }
}
