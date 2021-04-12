package org.folio.dew.client;

import org.folio.dew.domain.dto.ServicePoints;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "service-points")
public interface ServicePointClient {

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ServicePoints get(@RequestParam String query, @RequestParam long limit);

}
