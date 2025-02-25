package org.folio.dew.batch.acquisitions.edifact.jobs;

import static org.folio.dew.domain.dto.JobParameterNames.EDIFACT_ORDERS_EXPORT;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.dew.batch.ExecutionContextUtils;
import org.folio.dew.batch.acquisitions.edifact.PurchaseOrdersToEdifactMapper;
import org.folio.dew.batch.acquisitions.edifact.exceptions.EdifactException;
import org.folio.dew.batch.acquisitions.edifact.exceptions.OrderNotFoundException;
import org.folio.dew.batch.acquisitions.edifact.services.OrdersService;
import org.folio.dew.domain.dto.CompositePoLine;
import org.folio.dew.domain.dto.CompositePurchaseOrder;
import org.folio.dew.domain.dto.JobParameterNames;
import org.folio.dew.domain.dto.VendorEdiOrdersExportConfig;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor
@Component
@StepScope
@Log4j2
public class MapToEdifactTasklet implements Tasklet {
  private final ObjectMapper ediObjectMapper;

  private final OrdersService ordersService;
  private final PurchaseOrdersToEdifactMapper purchaseOrdersToEdifactMapper;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    log.info("Execute MapToEdifactTasklet");
    var jobParameters = chunkContext.getStepContext().getJobParameters();
    var ediExportConfig = ediObjectMapper.readValue((String)jobParameters.get(EDIFACT_ORDERS_EXPORT), VendorEdiOrdersExportConfig.class);
    validateEdiExportConfig(ediExportConfig);

    List<CompositePurchaseOrder> compOrders = getCompPOList(ediExportConfig);
    // save poLineIds in memory
    persistPoLineIds(chunkContext, compOrders);

    String jobName = jobParameters.get(JobParameterNames.JOB_NAME).toString();
    String edifactOrderAsString = purchaseOrdersToEdifactMapper.convertOrdersToEdifact(compOrders, ediExportConfig, jobName);
    // save edifact file content in memory
    ExecutionContextUtils.addToJobExecutionContext(chunkContext.getStepContext().getStepExecution(), "edifactOrderAsString", edifactOrderAsString, "");
    return RepeatStatus.FINISHED;
  }

  private void validateEdiExportConfig(VendorEdiOrdersExportConfig ediExportConfig) {
    var ediConfig = ediExportConfig.getEdiConfig();
    Optional<Integer> port = Optional.ofNullable(ediExportConfig.getEdiFtp().getFtpPort());

    if (StringUtils.isEmpty(ediConfig.getLibEdiCode()) || ediConfig.getLibEdiType() == null
      || StringUtils.isEmpty(ediConfig.getVendorEdiCode()) || ediConfig.getVendorEdiType() == null) {
      throw new EdifactException("Export configuration is incomplete, missing library EDI code/Vendor EDI code");
    }

    if (port.isEmpty()) {
      throw new EdifactException("Export configuration is incomplete, missing FTP/SFTP Port");
    }
  }

  private List<CompositePurchaseOrder> getCompPOList(VendorEdiOrdersExportConfig ediConfig) {
    var poQuery = buildPurchaseOrderQuery(ediConfig);

    var orders = ordersService.getCompositePurchaseOrderByQuery(poQuery, Integer.MAX_VALUE);

    var compOrders = orders.getPurchaseOrders()
      .stream().sequential()
      .map(order -> ordersService.getCompositePurchaseOrderById(order.getId()))
      .map(order -> order.compositePoLines(poLineFilteredOrder(order, ediConfig)))
      .filter(order -> !order.getCompositePoLines().isEmpty())
      .collect(Collectors.toList());

    log.debug("composite purchase orders: {}", compOrders);

    if (compOrders.isEmpty()) {
      throw new OrderNotFoundException("Orders for export not found", false);
    }
    return compOrders;
  }

  private String buildPurchaseOrderQuery(VendorEdiOrdersExportConfig ediConfig) {
    var workflowStatusFilter = "workflowStatus==Open";
    var vendorFilter = String.format(" and vendor==%s", ediConfig.getVendorId());
    var automaticExportFilter = " and poLine.automaticExport==true";
    var notManualFilter = " and cql.allRecords=1 NOT manualPo==true";
    var resultQuery = "(" + workflowStatusFilter + vendorFilter + automaticExportFilter + notManualFilter + ")";
    log.info("GET purchase orders query: {}", resultQuery);
    return resultQuery;
  }

  private void persistPoLineIds(ChunkContext chunkContext, List<CompositePurchaseOrder> compOrders) throws JsonProcessingException {
    var polineIds = compOrders.stream()
      .flatMap(ord -> ord.getCompositePoLines().stream())
      .map(CompositePoLine::getId)
      .collect(Collectors.toList());
    ExecutionContextUtils.addToJobExecutionContext(chunkContext.getStepContext().getStepExecution(),"polineIds", ediObjectMapper.writeValueAsString(polineIds),"");
  }

  private List<CompositePoLine> poLineFilteredOrder(CompositePurchaseOrder order, VendorEdiOrdersExportConfig ediConfig) {
    return order.getCompositePoLines().stream()
      .filter(CompositePoLine::getAutomaticExport)
      // comment filters for development time
      // fix filter after implementation of re-export logic
      .filter(poLine -> poLine.getLastEDIExportDate() == null)
      .filter(poLine -> ediConfig.getEdiConfig().getDefaultAcquisitionMethods().contains(poLine.getAcquisitionMethod()))
      .filter(poLine -> {
        if (ediConfig.getIsDefaultConfig() != null && ediConfig.getIsDefaultConfig()) {
          return StringUtils.isEmpty(poLine.getVendorDetail().getVendorAccount());
        }
        return ediConfig.getEdiConfig().getAccountNoList().contains(poLine.getVendorDetail().getVendorAccount());
      })
      .collect(Collectors.toList());
  }


}
