package io.mosip.registration.dto.payments;

import java.io.Serializable;
import java.util.Map;

import lombok.Data;

@Data
public class CheckPRNStatusResponseDTO implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String amountPaid;
    private String countyName;
    private String currency;
    private String datePaid;
    private String districtName;
    private String errorCode;
    private String errorDesc;
    private String feesPerUnit;
    private String instrumentID;
    private String landlineNumber;
    private String mdaName;
    private String maxUnit;
    private String minUnit;
    private String mobileNumber;
    private String noOfUnits;
    private String prn;
    private String paymentBank;
    private String paymentExpiryDate;
    private String paymentMode;
    private String realizationDate;
    private String referenceNumber;
    private String searchCode;
    private String stationName;
    private String statusCode;
    private String statusDesc;
    private String subcountyName;
    private String tin;
    private String taxHeadCode;
    private String taxHeadName;
    private String taxPayerEmail;
    private String taxPayerName;
    private String villageName;
	private Map<String,String> eligiblePaidForServiceTypes;
}
