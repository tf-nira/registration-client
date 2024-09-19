package io.mosip.registration.util.common;

import org.springframework.stereotype.Component;

import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;

import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
 kar Nin validator for validating with new regex pattern of nin 
 
 */
@Component
public class NinValidator {
	public boolean validate(String input) {
		ResourceBundle regxpat = ResourceBundle.getBundle("spring");
		String regexPattern = regxpat.getString("regex.pattern");
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(input);
        if(matcher.matches()) {
        	return true;
        }
        else {
        	throw new InvalidIDException("NIN 1","Enter a Valid NIN");
        }
    }
}
