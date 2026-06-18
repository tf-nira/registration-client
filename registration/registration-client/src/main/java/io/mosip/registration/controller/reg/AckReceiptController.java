package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import io.mosip.registration.api.printer.PrinterStatusChecker;
import io.mosip.registration.api.thermal.printer.ThermalPrinter;
import javafx.collections.ObservableSet;
import javafx.print.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
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
				RegistrationConstants.APPLICATION_ID, "Printing the Acknowledgement Thermal Receipt");
		
		try {
			LOGGER.info("About to parse slipStringWriter");

			if (slipStringWriter == null) {
				LOGGER.error("slipStringWriter is NULL");
				return;
			}

			String slipContent = slipStringWriter.toString();

			LOGGER.info("Slip content length : {}", slipContent.length());

			Document doc = Jsoup.parse(slipContent);

			LOGGER.info("Document parsed successfully");
	        Element qrImg = doc.selectFirst("img.qrimage");
	        
	        String path = "C:/Thermal_images/";
	        File pathFile = new File(path);
	        if (!pathFile.exists()) {
	            pathFile.mkdirs();
	        }
	        
	        File[] oldQrImages = pathFile.listFiles(f -> f.getName().startsWith("qr_image") && f.getName().endsWith(".png"));
	        if (oldQrImages != null) {
		        for (File file : oldQrImages) {
		            boolean deleted = file.delete();
		            if (!deleted) {
		                LOGGER.warn("Failed to delete old QR image: " + file.getAbsolutePath());
		            }
		        }
		    }

	        if (qrImg != null) {
	            String src = qrImg.attr("src");
	            if (src.startsWith("data:image")) {
	            	LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
	        				RegistrationConstants.APPLICATION_ID, "Updating qr code image");
	                String base64 = src.split(",")[1];
	                byte[] qrBytes = Base64.getDecoder().decode(base64);
	                BufferedImage qrImage = ImageIO.read(new ByteArrayInputStream(qrBytes));
	                
		            File qrFile = new File(path + "qr_image_" + System.currentTimeMillis() + ".png");
		            ImageIO.write(qrImage, "png", qrFile);
		            
		            Thread.sleep(500);
		            
		            String fileUrl = qrFile.toURI().toString();
		            qrImg.attr("src", fileUrl);
		            qrImg.attr("width", "100");
		            qrImg.attr("height", "100");
		            LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
	        				RegistrationConstants.APPLICATION_ID, "Updated qr code image");
	            }
	        }
			
			BufferedImage rendered = renderHtmlToImage(doc.html(), 640);
			rendered = zoomImage(rendered, 575);
	        saveAsMonochromeBmp(rendered, path + "print_image.bmp");
	        
	        LOGGER.info("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
    				RegistrationConstants.APPLICATION_ID, "Receipt image saved");
			
			ThermalPrinter thermalPrinter = ThermalPrinter.INSTANCE;
			long printerID = thermalPrinter.POS_Port_OpenA("SP-USB1", 1002, false, null);

			if ((int)printerID < 0) {
			    generateAlert(RegistrationConstants.ALERT_INFORMATION, "Thermal printer not connected");
			    LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
        				RegistrationConstants.APPLICATION_ID, "Thermal printer port open failed, status: " + (int)printerID);
			} else {
			    long printerStatus = thermalPrinter.POS_Status_RTQueryStatus(printerID);

			    if ((int)printerStatus == 1) {
			        generateAlert(RegistrationConstants.ALERT_INFORMATION, "Thermal printer is out of paper");
			    } else if ((int)printerStatus == 0) {
			    	long printStatus = thermalPrinter.POS_Output_PrintBmpDirectA(printerID, path + "print_image.bmp");
			        if ((int)printStatus != 0) {
			            generateAlert(RegistrationConstants.ALERT_INFORMATION, "Thermal printer connected but print failed");
			            LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
		        				RegistrationConstants.APPLICATION_ID, "Failed to print data with thermal printer, status: " + (int)printStatus);
			        } else {
			        	long feedStatus = thermalPrinter.POS_Control_FeedLines(printerID, 100);
			        	if ((int) feedStatus != 0) {
			        	    LOGGER.warn("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
			        	        RegistrationConstants.APPLICATION_ID, "Feed after print failed, status: " + (int) feedStatus);
			        	}
			        	generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.PRINT_INITIATION_SUCCESS);
			        }
			    } else {
			        generateAlert(RegistrationConstants.ALERT_INFORMATION, "Thermal printer not connected");
			        LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
	        				RegistrationConstants.APPLICATION_ID, "Thermal printer not connected, status: " + (int)printerStatus);
			    }

			    thermalPrinter.POS_Port_Close(printerID);
			}
		} catch (Exception e) {
			generateAlert(RegistrationConstants.ALERT_INFORMATION,
					RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PRINT_INITIATION_FAILED));
			LOGGER.error("REGISTRATION - UI - ACK_RECEIPT_CONTROLLER", RegistrationConstants.APPLICATION_NAME,
    				RegistrationConstants.APPLICATION_ID, "Exception while printing slip", e);
		}
	}
	
	private BufferedImage renderHtmlToImage(String html, int width) {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);

        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = new StyleSheet();
        styleSheet.addRule("body { padding-top: 20px; padding-bottom: 20px; margin: 0; }");
        kit.setStyleSheet(styleSheet);
        pane.setEditorKit(kit);
        
        pane.setText(html);
        
        pane.setSize(width, Short.MAX_VALUE);
        Dimension preferredSize = pane.getPreferredSize();
//      preferredSize.width = width;
        pane.setSize(preferredSize);

        BufferedImage image = new BufferedImage(preferredSize.width, preferredSize.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        pane.paint(g);
        g.dispose();
        return image;
    }
	
	private BufferedImage zoomImage(BufferedImage original, int targetWidth) {
	    int originalWidth = original.getWidth();
	    int originalHeight = original.getHeight();

	    double scaleFactor = (double) targetWidth / originalWidth;
	    int targetHeight = (int) (originalHeight * scaleFactor);

	    BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
	    Graphics2D g2 = scaledImage.createGraphics();
	    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	    g2.drawImage(original, 0, 0, targetWidth, targetHeight, null);
	    g2.dispose();

	    return scaledImage;
	}
	
	private void saveAsMonochromeBmp(BufferedImage original, String outputPath) throws IOException {
        BufferedImage gray = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();

        // Dithering / binarization for 1-bit BMP
        BufferedImage mono = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2 = mono.createGraphics();
        g2.drawImage(gray, 0, 0, null);
        g2.dispose();

        ImageIO.write(mono, "bmp", new File(outputPath));
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
