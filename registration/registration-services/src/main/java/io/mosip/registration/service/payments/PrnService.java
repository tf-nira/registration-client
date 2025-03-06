package io.mosip.registration.service.payments;


import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.dto.payments.CheckPRNInTransLogsRequestDTO;
import io.mosip.registration.dto.payments.CheckPRNInTransLogsResponseDTO;
import io.mosip.registration.dto.payments.CheckPRNStatusRequestDTO;
import io.mosip.registration.dto.payments.CheckPRNStatusResponseDTO;
import io.mosip.registration.dto.payments.ConsumePRNRequestDTO;
import io.mosip.registration.dto.payments.ConsumePRNResponseDTO;
import io.mosip.registration.dto.payments.PrnMainResponseWrapperDTO;


/**
 * This service class helps in the checking of PRN status and consuming of PRNs in the 
 * NIRA Payment Gateway service
 * 
 * 
 * @author Ibrahim Nkambo
 */


@Service
public class PrnService {
	
	private static final Logger LOGGER = AppConfig.getLogger(PrnService.class);
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Value("${nira.payment.gateway.service.check-prn-status}")
	private String urlCheckPrnStatus;
	
	@Value("${nira.payment.gateway.service.check-transaction-logs}")
	private String urlCheckTransLog;
	
	@Value("${nira.payment.gateway.service.consume-prn}")
	private String urlConsumePrn;
	
	@Autowired
	ObjectMapper objectMapper;

	
	public PrnMainResponseWrapperDTO<CheckPRNStatusResponseDTO> checkPRNStatus(String prn) {
	    CheckPRNStatusRequestDTO requestDTO = new CheckPRNStatusRequestDTO();
	    requestDTO.setPrn(prn);

	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);

	    try {
	        LOGGER.info("Sending request to check PRN status for PRN: {}", prn);

	        @SuppressWarnings("unchecked")
	        PrnMainResponseWrapperDTO<CheckPRNStatusResponseDTO> responseWrapper = 
	            (PrnMainResponseWrapperDTO<CheckPRNStatusResponseDTO>) sendHttpRequest(
	                urlCheckPrnStatus, HttpMethod.POST, headers, requestDTO, PrnMainResponseWrapperDTO.class
	            );

	        LOGGER.info("Received response from PRN status check: {}", responseWrapper);
	        return responseWrapper;

	    } catch (Exception e) {
	        LOGGER.error("Unexpected error occurred while checking PRN status for PRN: {}", prn, e);

	        // Return an error response
	        return createErrorResponse("Internal Server Error");
	    }
	}

	private <T> PrnMainResponseWrapperDTO<T> createErrorResponse(String errorMessage) {
	    PrnMainResponseWrapperDTO<T> errorResponse = new PrnMainResponseWrapperDTO<>();
	    List<ServiceError> errors = new ArrayList<>();
	    ServiceError exception = new ServiceError();
	    exception.setMessage(errorMessage);
	    errors.add(exception);
	    errorResponse.setErrors(errors);
	    return errorResponse;
	}
	
	public PrnMainResponseWrapperDTO<CheckPRNInTransLogsResponseDTO> checkPrnInTransLogs(String prn) {
	    CheckPRNInTransLogsRequestDTO requestDTO = new CheckPRNInTransLogsRequestDTO();
	    requestDTO.setPrn(prn);

	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);

	    try {
	        LOGGER.info("Sending request to check PRN in transaction logs for PRN: {}", prn);

	        @SuppressWarnings("unchecked")
	        PrnMainResponseWrapperDTO<CheckPRNInTransLogsResponseDTO> responseWrapper = 
	            (PrnMainResponseWrapperDTO<CheckPRNInTransLogsResponseDTO>) sendHttpRequest(
	                urlCheckTransLog, HttpMethod.POST, headers, requestDTO, PrnMainResponseWrapperDTO.class
	            );

	        LOGGER.info("Received response from transaction logs check: {}", responseWrapper);

	        // Handle response errors
	        if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
	            LOGGER.error("Errors in the returned response: {}", responseWrapper.getErrors());
	            return createErrorResponse("PRN Transaction Log Check Failed");
	        }

	        return responseWrapper;

	    } catch (Exception e) {
	        LOGGER.error("Unexpected error while checking PRN in transaction logs for PRN: {}", prn, e);
	        return createErrorResponse("Internal Server Error");
	    }
	}

	
	public ConsumePRNResponseDTO consumePrn(String prn, String registrationId) {
		ConsumePRNRequestDTO requestDTO = new ConsumePRNRequestDTO();
	    requestDTO.setPrn(prn);
	    requestDTO.setRegId(registrationId);

	    ConsumePRNResponseDTO response = null;
	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_JSON);

	    PrnMainResponseWrapperDTO<?> returnedResponse = null;

	    try {
	        returnedResponse = sendHttpRequest(
	            urlConsumePrn, HttpMethod.POST, headers, requestDTO, PrnMainResponseWrapperDTO.class
	        );

	        if (returnedResponse == null) {
	            LOGGER.warn("The returned response is null, unable to check PRN in transc logs.");
	            return null;
	        }

	        if (returnedResponse.getResponse() == null && returnedResponse.getErrors() != null
	            && !returnedResponse.getErrors().isEmpty()) {
	            LOGGER.error("Errors in the returned response: {}", returnedResponse.getErrors().get(0).getMessage());
	            return null;
	        }

	        if (returnedResponse.getResponse() != null && !"".equals(returnedResponse.getResponse())) {
	            response = objectMapper.convertValue(returnedResponse.getResponse(), ConsumePRNResponseDTO.class);
	        }
	    } catch (Exception e) {
	        LOGGER.error("Error occurred while checking PRN status: {}", e.getMessage(), e);
	    }

	    return response;
		
	}


    private <T> T sendHttpRequest( String url, HttpMethod httpMethod, HttpHeaders headers, Object requestBody, Class<T> responseType) {

        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<T> response = restTemplate.exchange(url, httpMethod, entity, responseType);
        return response.getBody();
    }
	
}
