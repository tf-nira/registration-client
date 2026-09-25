package io.mosip.registration.dao.impl;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.dao.DynamicFieldDAO;
import io.mosip.registration.dto.mastersync.DynamicFieldValueDto;
import io.mosip.registration.entity.DynamicField;
import io.mosip.registration.repositories.DynamicFieldRepository;
import io.mosip.registration.util.mastersync.MapperUtils;

@Repository
public class DynamicFieldDAOImpl implements DynamicFieldDAO {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(DynamicFieldDAOImpl.class);

	
	@Autowired
	private DynamicFieldRepository dynamicFieldRepository;
	
	@Override
	public DynamicField getDynamicField(String fieldName, String langCode) {
		LOGGER.debug("fetching the dynamic field >>> {} for langCode >>> {}" ,fieldName , langCode);

		return dynamicFieldRepository.findByIsActiveTrueAndNameAndLangCode(fieldName, langCode);
	}

	@Override
	public List<DynamicFieldValueDto> getDynamicFieldValues(String fieldName, String langCode) {

	    LOGGER.debug("Fetching the valueJSON");

	    DynamicField dynamicField = getDynamicField(fieldName, langCode);

	    try {
	        String valueJson = (dynamicField != null) ? dynamicField.getValueJson() : "[]";

	        List<DynamicFieldValueDto> fields = MapperUtils.convertJSONStringToDto(
	            valueJson == null ? "[]" : valueJson,
	            new TypeReference<List<DynamicFieldValueDto>>() {});

	        if (fields != null) {

	        	//Checking the value "Uganda" or "None" is present or not
	            boolean hasUGA = fields.stream().anyMatch(f -> "UGA".equals(f.getCode()));
	            boolean hasNone = fields.stream().anyMatch(f -> "None".equalsIgnoreCase(f.getValue()));

	            Set<String> ugaPriorityFields = Set.of(
	            	    "residenceStatus", 
	            	    "applicantBirthPlace", 
	            	    "applicantOriginPlace", 
	            	    "fatherResidence", 
	            	    "fatherOrigin", 
	            	    "motherResidence", 
	            	    "motherOrigin", 
	            	    "guardianResidence",
	            	    "CountryCode2",
	            	    "CountryCode"
	            	);

	            fields.sort((d1, d2) -> {
	                String code1 = d1.getCode();
	                String code2 = d2.getCode();
	                String value1 = d1.getValue();
	                String value2 = d2.getValue();

	                // UGA comes first only for certain fields
	                if (hasUGA && ugaPriorityFields.contains(fieldName)) {
	                    if ("UGA".equals(code1)) return -1;
	                    if ("UGA".equals(code2)) return 1;
	                }

	                boolean isDisabilitiesField = "disabilities".equals(fieldName);

	                boolean isNone1 = "None".equalsIgnoreCase(value1);
	                boolean isNone2 = "None".equalsIgnoreCase(value2);

	                boolean isOther1 = value1 != null && ("Other".equalsIgnoreCase(value1) || "Others".equalsIgnoreCase(value1) ||
	                    value1.matches("^Other \\(\\d+\\).*"));
	                
	                boolean isOther2 = value2 != null && ("Other".equalsIgnoreCase(value2) || "Others".equalsIgnoreCase(value2) ||
	                    value2.matches("^Other \\(\\d+\\).*"));

	                if (isDisabilitiesField && hasNone && !hasUGA) {
	                    if (isNone1) return -1;
	                    if (isNone2) return 1;
	                }

	                if (!isDisabilitiesField) {
	                    if (isOther1 && !isOther2) return 1;
	                    if (!isOther1 && isOther2) return -1;

	                    if (isNone1 && !isNone2) {
	                        return isOther2 ? -1 : 1;
	                    }
	                    if (!isNone1 && isNone2) {
	                        return isOther1 ? 1 : -1;
	                    }
	                }

	                if (isNone1 && isNone2) return value1.compareTo(value2);
	                if (isOther1 && isOther2) return value1.compareTo(value2);

	                return code1.compareTo(code2);
	            });
	        }

	        return fields;

	    } catch (IOException e) {
	        LOGGER.error("Unable to parse value json for dynamic field: ", e);
	    }

	    return null;
	}

}
