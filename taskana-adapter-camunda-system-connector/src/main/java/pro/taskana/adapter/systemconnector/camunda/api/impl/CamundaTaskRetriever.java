package pro.taskana.adapter.systemconnector.camunda.api.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import pro.taskana.adapter.camunda.outbox.rest.CamundaTaskEvent;
import pro.taskana.adapter.camunda.outbox.rest.CamundaTaskEventListResource;
import pro.taskana.adapter.systemconnector.api.ReferencedTask;

/** Retrieves new tasks from camunda that have been started or finished by camunda. */
@Component
public class CamundaTaskRetriever {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaTaskRetriever.class);

  @Autowired private HttpHeaderProvider httpHeaderProvider;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RestTemplate restTemplate;

  public List<ReferencedTask> retrieveNewStartedCamundaTasks(String camundaSystemTaskEventUrl) {

    LOGGER.debug("entry to retrieveNewStartedCamundaTasks.");

    List<CamundaTaskEvent> camundaTaskEvents =
        getCamundaTaskEvents(
            camundaSystemTaskEventUrl, CamundaSystemConnectorImpl.URL_GET_CAMUNDA_CREATE_EVENTS);

    List<ReferencedTask> referencedTasks =
        getReferencedTasksFromCamundaTaskEvents(camundaTaskEvents);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("exit from retrieveActiveCamundaTasks. Retrieved Tasks: {}", referencedTasks);
    }
    return referencedTasks;
  }

  public List<ReferencedTask> retrieveFinishedCamundaTasks(String camundaSystemUrl) {
    LOGGER.debug("entry to retrieveFinishedCamundaTasks. CamundSystemURL = {} ", camundaSystemUrl);

    List<CamundaTaskEvent> camundaTaskEvents =
        getCamundaTaskEvents(
            camundaSystemUrl, CamundaSystemConnectorImpl.URL_GET_CAMUNDA_FINISHED_EVENTS);

    List<ReferencedTask> referencedTasks =
        getReferencedTasksFromCamundaTaskEvents(camundaTaskEvents);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("exit from retrieveFinishedCamundaTasks. Retrieved Tasks: {}", referencedTasks);
    }
    return referencedTasks;
  }

  private List<CamundaTaskEvent> getCamundaTaskEvents(
      String camundaSystemTaskEventUrl, String eventSelector) {

    String requestUrl =
        camundaSystemTaskEventUrl + CamundaSystemConnectorImpl.URL_OUTBOX_REST_PATH + eventSelector;

    HttpHeaders headers = httpHeaderProvider.getHttpHeadersForOutboxRestApi();
    LOGGER.debug(
        "retrieving camunda task event resources with url {} and headers {}", requestUrl, headers);
    ResponseEntity<CamundaTaskEventListResource> responseEntity =
        restTemplate.exchange(
            requestUrl,
            HttpMethod.GET,
            new HttpEntity<Object>(headers),
            CamundaTaskEventListResource.class);

    CamundaTaskEventListResource camundaTaskEventListResource = responseEntity.getBody();

    List<CamundaTaskEvent> retrievedEvents = camundaTaskEventListResource.getCamundaTaskEvents();
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("retrieved camunda task events {}", retrievedEvents);
    }

    return retrievedEvents;
  }

  private List<ReferencedTask> getReferencedTasksFromCamundaTaskEvents(
      List<CamundaTaskEvent> camundaTaskEvents) {

    List<ReferencedTask> referencedTasks = new ArrayList<>();

    for (CamundaTaskEvent camundaTaskEvent : camundaTaskEvents) {

      String referencedTaskJson = camundaTaskEvent.getPayload();

      try {

        ReferencedTask referencedTask =
            objectMapper.readValue(referencedTaskJson, ReferencedTask.class);
        referencedTask.setOutboxEventId(String.valueOf(camundaTaskEvent.getId()));
        referencedTask.setOutboxEventType(String.valueOf(camundaTaskEvent.getType()));
        referencedTasks.add(referencedTask);

      } catch (IOException e) {

        LOGGER.warn(
            "Caught {} while trying to create ReferencedTasks "
                + " out of CamundaTaskEventResources. RefTaskJson = {}",
            e,
            referencedTaskJson);
      }
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("retrieved reference tasks {}", referencedTasks);
    }
    return referencedTasks;
  }
}
