package io.mosip.registration.util.control.impl;

import java.util.*;

import io.mosip.registration.controller.ClientApplication;
import javafx.scene.control.Tooltip;
import org.springframework.context.ApplicationContext;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.controller.FXUtils;
import io.mosip.registration.controller.Initialization;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.service.sync.MasterSyncService;
import io.mosip.registration.util.common.DemographicChangeActionHandler;
import io.mosip.registration.util.control.FxControl;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class ToggleButtonFxControl extends FxControl {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(ToggleButtonFxControl.class);
	
	private DemographicChangeActionHandler demographicChangeActionHandler;

	public ToggleButtonFxControl() {
		ApplicationContext applicationContext = ClientApplication.getApplicationContext();
		demographicChangeActionHandler = applicationContext.getBean(DemographicChangeActionHandler.class);
	}

	@Override
	public FxControl build(UiFieldDTO uiFieldDTO) {
		this.uiFieldDTO = uiFieldDTO;
		this.control = this;
		this.node = create(uiFieldDTO, getRegistrationDTo().getSelectedLanguagesByApplicant().get(0));
		return this.control;
	}

	private VBox create(UiFieldDTO uiFieldDTO, String langCode) {
		String fieldName = uiFieldDTO.getId();

		/** Container holds title, fields and validation message elements */
		VBox simpleTypeVBox = new VBox();
		simpleTypeVBox.setId(fieldName + RegistrationConstants.VBOX);
		simpleTypeVBox.setSpacing(5);

		List<String> labels = new ArrayList<>();
		getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(lCode -> {
			labels.add(this.uiFieldDTO.getLabel().get(lCode));});

		double prefWidth = simpleTypeVBox.getPrefWidth();
		
		ToggleButton button = getButton(fieldName,
				String.join(RegistrationConstants.SLASH, labels) + getMandatorySuffix(uiFieldDTO),
				prefWidth, false);
		
		setListener(button);
		simpleTypeVBox.getChildren().add(button);
		simpleTypeVBox.getChildren().add(getLabel(uiFieldDTO.getId() + RegistrationConstants.ERROR_MSG, null,
				RegistrationConstants.DemoGraphicFieldMessageLabel, false, simpleTypeVBox.getPrefWidth()));
		changeNodeOrientation(simpleTypeVBox, langCode);
		return simpleTypeVBox;
	}
	
	private ToggleButton getButton(String id, String titleText, double prefWidth,
			boolean isDisable) {
		ToggleButton button = new ToggleButton(titleText);
		button.setId(id);
		button.setPrefWidth(prefWidth);
		button.setDisable(isDisable);
		button.getStyleClass().add("control-toggle-button");
		return button;
	}

	@Override
	public void setData(Object data) {
		ToggleButton button = (ToggleButton) getField(uiFieldDTO.getId());
		getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), button == null ? "N"
								: button.isSelected() ? "Y" : "N");
	}

	@Override
	public void fillData(Object data) {
		selectAndSet(data);
	}

	@Override
	public Object getData() {
		return getRegistrationDTo().getDemographics().get(uiFieldDTO.getId());
	}


	@Override
	public boolean isValid() {
		if(requiredFieldValidator.isRequiredField(this.uiFieldDTO, getRegistrationDTo())){
			ToggleButton button = (ToggleButton) getField(uiFieldDTO.getId());
			return button == null ? false : button.isSelected() ? true : false;
		}
		return true;
	}

	@Override
	public boolean isEmpty() {
		ToggleButton button = (ToggleButton) getField(uiFieldDTO.getId());
		return button == null ? true : button.isSelected() ? false : true;
	}

	@Override
	public List<GenericDto> getPossibleValues(String langCode) {
		return null;
	}

	@Override
	public void setListener(Node node) {
		ToggleButton button = (ToggleButton) node;
		button.selectedProperty().addListener((options, oldValue, newValue) -> {
			getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), newValue ? "Y" : "N");
			
			// handling other handlers
			demographicChangeActionHandler.actionHandle((Pane) getNode(), node.getId(),
					uiFieldDTO.getChangeAction());
			// Group level visibility listeners
			if(uiFieldDTO.getDependentFields() != null && !uiFieldDTO.getDependentFields().isEmpty()) {
				refreshDependentFields(uiFieldDTO.getDependentFields());
			}
		});
	}

	@Override
	public void selectAndSet(Object data) {
		ToggleButton button = (ToggleButton) getField(uiFieldDTO.getId());
		if(data == null) {
			button.setSelected(false);
			return;
		}

		button.setSelected(data != null && ((String)data).equals("Y") ? true : false);
	}

}
