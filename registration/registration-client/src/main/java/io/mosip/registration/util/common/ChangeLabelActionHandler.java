package io.mosip.registration.util.common;

import io.mosip.registration.controller.GenericController;
import io.mosip.registration.util.control.FxControl;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import org.springframework.stereotype.Component;

@Component
public class ChangeLabelActionHandler extends ChangeActionHandler {

    @Override
    public String getActionClassName() {
        return "changeLabel";
    }

    @Override
    public void handle(Pane parentPane, String source, String[] args) {
        for(String arg : args) {
            String[] parts = arg.split("=");
            if(parts.length == 2) {
            	FxControl control = GenericController.getFxControlMap().get(parts[0]);
            	
            	if (parts[1].equals("disable")) {
            		VBox vbox = (VBox) control.getNode();
    				
    				for (Node node : vbox.getChildren()) {
    					if (node instanceof ToggleButton) {
    						ToggleButton button = (ToggleButton) node;
    						button.setDisable(!button.isDisable());
    					}
    				}
            	} else {
                	VBox vbox = (VBox) control.getNode();
    				
    				for (Node node : vbox.getChildren()) {
    					if (node instanceof TitledPane) {
    						TitledPane titledPane = (TitledPane) node;
    						titledPane.setText(parts[1]);
    					}
    				}
            	}
            }
        }
    }
}
