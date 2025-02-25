package org.folio.dew.batch.bulkedit.jobs.processidentifiers;

import lombok.RequiredArgsConstructor;
import org.folio.dew.batch.AbstractStorageStreamAndJsonWriter;
import org.folio.dew.batch.CsvAndJsonWriter;
import org.folio.dew.batch.JobCompletionNotificationListener;
import org.folio.dew.batch.bulkedit.jobs.BulkEditUserProcessor;
import org.folio.dew.domain.dto.ExportType;
import org.folio.dew.domain.dto.ItemIdentifier;
import org.folio.dew.domain.dto.User;
import org.folio.dew.domain.dto.UserFormat;
import org.folio.dew.error.BulkEditException;
import org.folio.dew.error.BulkEditSkipListener;
import org.folio.dew.repository.LocalFilesStorage;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;

import static org.folio.dew.domain.dto.EntityType.USER;
import static org.folio.dew.domain.dto.UserFormat.getUserColumnHeaders;
import static org.folio.dew.domain.dto.UserFormat.getUserFieldsArray;
import static org.folio.dew.utils.Constants.CHUNKS;
import static org.folio.dew.utils.Constants.JOB_NAME_POSTFIX_SEPARATOR;

@Configuration
@RequiredArgsConstructor
public class BulkEditUserIdentifiersJobConfig {
  private final BulkEditUserProcessor bulkEditUserProcessor;
  private final UserFetcher userFetcher;
  private final BulkEditSkipListener bulkEditSkipListener;
  private final LocalFilesStorage localFilesStorage;

  @Bean
  @StepScope
  public AbstractStorageStreamAndJsonWriter<User, UserFormat, LocalFilesStorage> csvUserWriter(
    @Value("#{jobParameters['tempOutputFilePath']}") String outputFileName) {
    return new CsvAndJsonWriter<>(outputFileName, getUserColumnHeaders(), getUserFieldsArray(), (field, i) -> field, localFilesStorage);
  }

  @Bean
  public Job bulkEditProcessUserIdentifiersJob(JobCompletionNotificationListener listener, Step bulkEditUserStep,
                                               JobRepository jobRepository) {
    return new JobBuilder(ExportType.BULK_EDIT_IDENTIFIERS + JOB_NAME_POSTFIX_SEPARATOR + USER.getValue(), jobRepository)
      .incrementer(new RunIdIncrementer())
      .listener(listener)
      .flow(bulkEditUserStep)
      .end()
      .build();
  }

  @Bean
  public Step bulkEditUserStep(FlatFileItemReader<ItemIdentifier> csvItemIdentifierReader,
      AbstractStorageStreamAndJsonWriter<User, UserFormat, LocalFilesStorage> csvUserWriter,
      IdentifiersWriteListener<UserFormat> identifiersWriteListener, JobRepository jobRepository,
      PlatformTransactionManager transactionManager) {
    return new StepBuilder("bulkEditUserStep", jobRepository)
      .<ItemIdentifier, UserFormat> chunk(CHUNKS, transactionManager)
      .reader(csvItemIdentifierReader)
      .processor(identifierUserProcessor())
      .faultTolerant()
      .skipLimit(1_000_000)
      .processorNonTransactional() // Required to avoid repeating BulkEditItemProcessor#process after skip.
      .skip(BulkEditException.class)
      .listener(bulkEditSkipListener)
      .writer(csvUserWriter)
      .listener(identifiersWriteListener)
      .build();
  }

  @Bean
  public CompositeItemProcessor<ItemIdentifier, UserFormat> identifierUserProcessor() {
    var processor = new CompositeItemProcessor<ItemIdentifier, UserFormat>();
    processor.setDelegates(Arrays.asList(userFetcher, bulkEditUserProcessor));
    return processor;
  }
}
