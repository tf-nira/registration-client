package io.mosip.registration.util.control.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.controller.ClientApplication;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;

import org.springframework.context.ApplicationContext;

import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.util.control.FxControl;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class TitleFxControl extends FxControl {

    /**
     * Instance of {@link Logger}
     */
    private static final Logger LOGGER = AppConfig.getLogger(TitleFxControl.class);
    public static final String HASH = "#";

    private AuditManagerService auditFactory;
    
    public TitleFxControl() {
        ApplicationContext applicationContext = ClientApplication.getApplicationContext();
        auditFactory = applicationContext.getBean(AuditManagerService.class);
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

        /** Container holds title, fields */
        VBox simpleTypeVBox = new VBox();
        simpleTypeVBox.setId(fieldName + RegistrationConstants.VBOX);
        simpleTypeVBox.setSpacing(5);

        List<String> labels = new ArrayList<>();
        getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(lCode -> {
            labels.add(this.uiFieldDTO.getLabel().get(lCode));});

        // The label is displayed as text, no checkbox interaction
        double prefWidth = simpleTypeVBox.getPrefWidth();

        TitledPane titlePane = createTitledPane(String.join(RegistrationConstants.SLASH, labels));
        
        // Create a label-like text instead of a checkbox
        //simpleTypeVBox.getChildren().add(getLabel(fieldName, String.join(RegistrationConstants.SLASH, labels), null, false, prefWidth));

        // Optional error message label for the field
        //simpleTypeVBox.getChildren().add(getLabel(uiFieldDTO.getId() + RegistrationConstants.ERROR_MSG, null,
        //        RegistrationConstants.DemoGraphicFieldMessageLabel, false, simpleTypeVBox.getPrefWidth()));

        simpleTypeVBox.getChildren().add(titlePane);
        changeNodeOrientation(simpleTypeVBox, langCode);
        return simpleTypeVBox;
    }
    
    private TitledPane createTitledPane(String label) {
		TitledPane titledPane = new TitledPane();
		titledPane.setText(label);
		titledPane.setExpanded(false);
		titledPane.setPrefWidth(950);
		titledPane.setStyle("-fx-font-weight: normal; -fx-font-size: 13px;"); // Set normal font weight
		titledPane.setAlignment(Pos.TOP_LEFT); // Important to fill the width

		// Create a GridPane to hold dependent fields
		GridPane sectionGridPane = new GridPane();
		sectionGridPane.setHgap(10);
		sectionGridPane.setVgap(10);
		sectionGridPane.setPadding(new Insets(10));
		sectionGridPane.setPrefWidth(950);
		sectionGridPane.setStyle("-fx-font-weight: normal; -fx-font-size: 13px; -fx-background-color: white;");
		sectionGridPane.setAlignment(Pos.TOP_LEFT); // Important to fill the width

		// Set column constraints for the sectionGridPane
		ColumnConstraints column1 = new ColumnConstraints();
		column1.setPercentWidth(33.33);
		column1.setHgrow(Priority.ALWAYS);
		ColumnConstraints column2 = new ColumnConstraints();
		column2.setPercentWidth(33.33);
		column2.setHgrow(Priority.ALWAYS);
		ColumnConstraints column3 = new ColumnConstraints();
		column3.setPercentWidth(33.33);
		column3.setHgrow(Priority.ALWAYS);
		sectionGridPane.getColumnConstraints().addAll(column1, column2, column3);

		titledPane.setContent(sectionGridPane);
		return titledPane;
	}

    // Simplified getLabel method to create a label text node
    protected Label getLabel(String id, String text, String type, boolean isDisable, double prefWidth) {
        // Here we are simply returning a label for the text. No input or interaction needed.
        javafx.scene.control.Label label = new javafx.scene.control.Label(text);
        label.setId(id);
        label.setPrefWidth(prefWidth);
        return label;
    }

    @Override
    public void setData(Object data) {
        // No checkbox interaction, so no need to store data related to checkbox state.
        auditFactory.audit(AuditEvent.REG_CHECKBOX_FX_CONTROL, Components.REG_DEMO_DETAILS, SessionContext.userId(),
                AuditReferenceIdTypes.USER_ID.getReferenceTypeId());
    }

    @Override
    public Object getData() {
        // Since this is just for displaying the label, we do not need to manage checkbox data.
        return null;
    }

    @Override
    public boolean isValid() {
        // No checkbox input, so always return true (no validation needed).
        return true;
    }

    @Override
    public boolean isEmpty() {
        // Since there is no input, we assume it's never empty.
        return true;
    }

    @Override
    public List<GenericDto> getPossibleValues(String langCode) {
        return null; // No possible values as there is no input
    }

    @Override
    public void setListener(Node node) {
        // No need for listeners as we are not interacting with the checkbox anymore.
    }

    @Override
    public void fillData(Object data) {
        // We are only displaying the label, so no need to fill data.
    }

    @Override
    public void selectAndSet(Object data) {
        // No checkbox selection, so no need to manage data in that regard.
    }
}
