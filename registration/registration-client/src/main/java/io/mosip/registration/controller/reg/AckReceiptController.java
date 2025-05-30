package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.sun.javafx.print.PrintHelper;
import com.sun.javafx.print.Units;
import io.mosip.registration.api.printer.PrinterStatusChecker;
import io.mosip.registration.api.thermal.printer.ThermalPrinter;
import javafx.collections.ObservableSet;
import javafx.embed.swing.SwingFXUtils;
import javafx.print.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Class for showing the Acknowledgement Receipt
 *
 * @author Himaja Dhanyamraju
 *
 */
@Controller
public class AckReceiptController extends BaseController implements Initializable {

	private static final Logger LOGGER = AppConfig.getLogger(AckReceiptController.class);

	@Autowired
	private PacketHandlerController packetController;

	private Writer stringWriter;
	private Writer slipStringWriter;

	@FXML
	protected GridPane rootPane;

	@FXML
	private WebView webView;
	@FXML
	private WebView slipWebView;

	@FXML
	private Button newRegistration;

	@FXML
	private Button print;

	// @FXML
	// private Button sendNotification;

	@FXML
	private ImageView newRegistrationBtnImgVw;
	@FXML
	private ImageView printImgVw;	
	@FXML
	private ImageView SendEmailImageView;
;

	@Autowired
	private SendNotificationController sendNotificationController;

	public void setStringWriter(Writer stringWriter) {
		this.stringWriter = stringWriter;
	}
	public void setSlipStringWriter(Writer slipStringWriter) {
		this.slipStringWriter = slipStringWriter;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
				"Page loading has been started");
		setImage(newRegistrationBtnImgVw, RegistrationConstants.NEW_REGISTRATION_IMG);
		setImage(printImgVw, RegistrationConstants.PRINTER_IMG);
		setImage(SendEmailImageView, RegistrationConstants.SEND_EMAIL_IMG);
		
		// setImagesOnHover();
		String notificationType = getValueFromApplicationContext(RegistrationConstants.MODE_OF_COMMUNICATION);
		/*
		 * if (notificationType != null && !notificationType.trim().isEmpty() &&
		 * !notificationType.equals("NONE")) {
		 *
		 * sendNotification.setVisible(false); } else {
		 * sendNotification.setVisible(false); }
		 */

		WebEngine engine = webView.getEngine();
		// loads the generated HTML template content into webview
		engine.loadContent(stringWriter.toString());




		LOGGER.info("REGISTRATION - UI - ACK-RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
				"Acknowledgement template has been loaded to webview");
	}

	/*
	 * private void setImagesOnHover() { Image sendEmailInWhite = new Image(
	 * getClass().getResourceAsStream(RegistrationConstants.
	 * SEND_EMAIL_FOCUSED_IMAGE_PATH)); Image sendEmailImage = new
	 * Image(getClass().getResourceAsStream(RegistrationConstants.
	 * SEND_EMAIL_IMAGE_PATH));
	 *
	 * sendNotification.hoverProperty().addListener((ov, oldValue, newValue) -> { if
	 * (newValue) { sendNotificationImageView.setImage(sendEmailInWhite); } else {
	 * sendNotificationImageView.setImage(sendEmailImage); } }); }
	 */

	/**
	 * To print the acknowledgement receipt after packet creation when the user
	 * clicks on print button.
	 *
	 * @param event - the event that happens on click of print button
	 */
	@FXML
	public void printReceiptThermal(ActionEvent event) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Printing the Acknowledgement Receipt");
		slipWebView.getEngine().loadContent(slipStringWriter.toString());
		
		WritableImage fxImage = slipWebView.snapshot(new SnapshotParameters(), null);
		BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
		
		try {
			File tempFile = File.createTempFile("receipt", ".bmp");
			ImageIO.write(bufferedImage, "bmp", tempFile);
			
			ThermalPrinter thermalPrinter = ThermalPrinter.INSTANCE;
			long printerID = thermalPrinter.POS_Port_OpenA("SP-USB1", 1002, false, null);

			if ((int)printerID < 0) {
			    generateAlert(RegistrationConstants.ALERT_INFORMATION, "Printer port open failed");
			} else {
			    long printerStatus = thermalPrinter.POS_Status_RTQueryStatus(printerID);

			    if ((int)printerStatus == 1) {
			        generateAlert(RegistrationConstants.ALERT_INFORMATION, "Printer is out of paper");
			    } else if ((int)printerStatus == 0) {
			        //long printStatus = thermalPrinter.POS_Output_PrintFontStringA(printerID, 0, 0, 0, 0, 0, slipStringWriter.toString());
			    	long printStatus = thermalPrinter.POS_Output_PrintBmpDirectA(printerID, tempFile.getAbsolutePath());
			        if ((int)printStatus != 0) {
			            generateAlert(RegistrationConstants.ALERT_INFORMATION, "Failed to send print data");
			        }
			    } else {
			        generateAlert(RegistrationConstants.ALERT_INFORMATION, "Printer not connected");
			    }

			    thermalPrinter.POS_Port_Close(printerID);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void printReceipt(ActionEvent event) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Printing the Acknowledgement Receipt");
		PrinterJob job = PrinterJob.createPrinterJob();
		if (job != null) {
			ObservableSet<Printer> installedPrinters = Printer.getAllPrinters();
			List<PrinterStatusChecker> connectedPrinters = PrinterStatusChecker.getPrintersWithStatus();
			boolean printerSelected = false;
			for (Printer printer : installedPrinters) {
				LOGGER.info( "selected printer in the loop"+ printer.getName());
				if (printer.getName().contains(getValueFromApplicationContext(RegistrationConstants.A6_THERMAL_PRINTER))) {
				//if (printer.getName().contains("80mm Series Printer")) {
					LOGGER.info("Skipping Thermal Printer: " + printer.getName());
				}
				else{
					boolean isConnected = connectedPrinters.stream().anyMatch(checker -> checker.getPrinterName().equalsIgnoreCase(printer.getName()));

					if (isConnected) {
						LOGGER.info("Using connected Normal printer: " + printer.getName());
						job.setPrinter(printer);
						job.getJobSettings().setJobName(getRegistrationDTOFromSession().getRegistrationId() + "_Ack");
						webView.getEngine().print(job);
						job.endJob();
						generateAlert(RegistrationConstants.ALERT_INFORMATION,
								RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PRINT_INITIATION_SUCCESS));
						printerSelected = true;
						LOGGER.info("Successfully sent print job to printer: " + printer.getName());

					}
					else {
						LOGGER.error("No Normal Printer.");
						generateAlert(RegistrationConstants.ALERT_INFORMATION,
								RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PRINT_INITIATION_FAILED_NORMAL_PRINTER_NOT_CONNECTED));
					}
					break;
			}


		}
	}
		else {
			LOGGER.error("Failed to create a print job.");
			generateAlert(RegistrationConstants.ALERT_INFORMATION,
					RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PRINT_INITIATION_FAILED_JOB));
		}
	}

	@FXML
	public void sendNotification(ActionEvent event) {
		LOGGER.debug("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to Send Notification Popup Window");

		sendNotificationController.init();
	}

	@FXML
	public void goToNewRegistration(ActionEvent event) {
		LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to New Registration Page after packet creation");

		clearRegistrationData();
		goToHomePageFromRegistration();
	}

	/**
	 * Go to home ack template.
	 */
	public void goToHomeAckTemplate() {
		try {
			BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
			if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
				clearOnboardData();
				clearRegistrationData();
			} else {
				SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
						RegistrationConstants.ENABLE);
			}
		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		} catch (RuntimeException runtimException) {
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					runtimException.getMessage() + ExceptionUtils.getStackTrace(runtimException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		}

	}

}