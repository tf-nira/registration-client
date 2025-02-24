package io.mosip.registration.controller.reg;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.itextpdf.text.pdf.PdfStructTreeController.returnType;
import antlr.StringUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.DocScannerFacade;
import io.mosip.registration.api.docscanner.DocScannerUtil;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import io.mosip.registration.api.signaturescanner.SignatureFacade;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.device.ScanPopUpViewController;
import io.mosip.registration.dto.packetmanager.DocumentDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.util.control.FxControl;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * {@code DocumentScanController} is to handle the screen of the Demographic
 * document section details
 *
 * @author M1045980
 * @since 1.0.0
 */
@Controller
public class DocumentScanController extends BaseController {

	private static final Logger LOGGER = AppConfig.getLogger(DocumentScanController.class);

	@Autowired
	private ScanPopUpViewController scanPopUpViewController;

	@FXML
	protected GridPane documentScan;

	@FXML
	private GridPane documentPane;

	@FXML
	protected ImageView docPreviewImgView;

	@FXML
	protected Label docPreviewNext;

	@FXML
	protected Label docPreviewPrev;

	@FXML
	protected Label docPageNumber;

	@FXML
	protected Label docPreviewLabel;
	@FXML
	public GridPane documentScanPane;

	@FXML
	private VBox docScanVbox;

	private List<BufferedImage> scannedPages;

	@FXML
	private Label registrationNavlabel;

	@FXML
	private Button continueBtn;
	@FXML
	private Button backBtn;
	@FXML
	private ImageView backImageView;
	@FXML
	private Label biometricExceptionReq;

	@Autowired
	private DocScannerFacade docScannerFacade;
	
	@Autowired
	private SignatureFacade signatureFacade;

	private String selectedScanDeviceName;

	private FxControl fxControl;

	private String subType;
	
	private DocScanDevice scanDevice;


	public void scan(Stage popupStage) {
		try {
			scanPopUpViewController.getScanningMsg().setVisible(true);
			if (scannedPages == null) {
				scannedPages = new ArrayList<>();
			}
			
			try {
				if(scanDevice != null) {
					signatureFacade.stopDevice(scanDevice);
				}
			} catch (Exception e){};
			
			Optional<DocScanDevice> result = null;
			String enabled = String.valueOf(ApplicationContext.map().get(RegistrationConstants.STUB_SCANNER_ENABLED));
			if(subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE) || subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE)) {
				result = signatureFacade.getConnectedDevices(enabled).stream().filter(d -> d.getId().equals(selectedScanDeviceName)).findFirst();
			} else {
				result =  docScannerFacade.getConnectedDevices(enabled).stream().filter(d -> d.getId().equals(selectedScanDeviceName)).findFirst();
			}

			
			if(!result.isPresent()) {
				LOGGER.error("No scan devices found");
				generateAlert(RegistrationConstants.ERROR,
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICE_FOUND));
				return;
			}

			scanDevice = result.get();
			scanDevice.setFrame(null);
			BufferedImage bufferedImage = null;
			
			if(subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE) || subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE)) {
				bufferedImage =signatureFacade.scanDocument(scanDevice, DeviceType.SIGNATURE_PAD.toString());
			} else {
				bufferedImage = docScannerFacade.scanDocument(scanDevice, getValueFromApplicationContext(RegistrationConstants.IMAGING_DEVICE_TYPE));
				
			}

			if (bufferedImage == null) {
				if(subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE) || subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE)) {
					LOGGER.error("Captured buffered image was null and Signature process timed out.");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_SIGNATURE_ERROR));
				} else {
					LOGGER.error("captured buffered image was null");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOCUMENT_ERROR));
				}
				return;
			}

			scannedPages.add(bufferedImage);
			scanPopUpViewController.getImageGroup().getChildren().clear();
			scanPopUpViewController.getImageGroup().getChildren().add(new ImageView(DocScannerUtil.getImage(bufferedImage)));
			scanPopUpViewController.getScanImage().setVisible(true);
			scanPopUpViewController.getScanningMsg().setVisible(false);
			scanPopUpViewController.showPreview(true);
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.DOC_CAPTURE_SUCCESS));

		} catch (RuntimeException exception) {
			LOGGER.error("Exception while scanning documents for registration", exception);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOCUMENT_ERROR));
		}catch (Exception e) {
			LOGGER.error("Exception while scanning documents for registration", e);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOCUMENT_ERROR));
		}
	}

	public byte[] captureAndConvertBufferedImage() throws Exception {
		String enabled = String.valueOf(ApplicationContext.map().get(RegistrationConstants.STUB_SCANNER_ENABLED));
		List<DocScanDevice> devices = docScannerFacade.getConnectedCameraDevices(enabled);

		byte[] byteArray = new byte[0];
		if(!devices.isEmpty()) {
			BufferedImage bufferedImage = docScannerFacade.scanDocument(devices.get(0), getValueFromApplicationContext(RegistrationConstants.IMAGING_DEVICE_TYPE));
			if (bufferedImage != null) {
				byteArray = DocScannerUtil.getImageBytesFromBufferedImage(bufferedImage);
			}
			// Enable Auto-Logout
			SessionContext.setAutoLogout(true);
			return byteArray;
		}
		throw new Exception("No Camera Devices connected");
	}


	/**
	 * This method is to select the device and initialize document scan pop-up
	 */
	private void initializeAndShowScanPopup(boolean isPreviewOnly,String subType) {
		List<DocScanDevice> devices = null;
		String enabled = String.valueOf(ApplicationContext.map().get(RegistrationConstants.STUB_SCANNER_ENABLED));
		LOGGER.info("Checking the StubScanner enable/disable : {}", enabled);
		if(subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE) || subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE)) {
			devices = signatureFacade.getConnectedDevices(enabled);
		} else {
			devices = docScannerFacade.getConnectedDevices(enabled);
		}
		
		LOGGER.info("Connected devices : {}", devices);

		if (devices.isEmpty()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICE_FOUND));
			return;
		}
		this.subType = subType;
		selectedScanDeviceName =  devices.get(0).getId() ;
		Optional<DocScanDevice> result = devices.stream().filter(d -> d.getId().equals(selectedScanDeviceName)).findFirst();
		LOGGER.info("Selected device name : {}", selectedScanDeviceName);

		if(!result.isPresent()) {
			LOGGER.info("No devices found for the selected device name : {}", selectedScanDeviceName);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICE_FOUND));
			return;
		}

		scanPopUpViewController.docScanDevice = result.get();
		scanPopUpViewController.init(this,
				RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOC_TITLE), subType);

		if(isPreviewOnly)
			scanPopUpViewController.setUpPreview();
		/*else
			scanPopUpViewController.docScanDevice = result.get();*/
	}

	public List<BufferedImage> getScannedPages() {
		return scannedPages;
	}

	public void setScannedPages(List<BufferedImage> scannedPages) {
		this.scannedPages = scannedPages;
	}

	public BufferedImage getScannedImage(int docPageNumber) {
		return scannedPages.get(docPageNumber <= 0 ? 0 : docPageNumber);
	}

	public boolean loadDataIntoScannedPages(String fieldId, String subType) throws IOException {
		
		DocumentDto documentDto = getRegistrationDTOFromSession().getDocuments().get(fieldId);
		String signature = "";
		if(subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE)) {
			signature = getRegistrationDTOFromSession().getDemographic(RegistrationConstants.SIGNATURE);
		} else if(subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE)) {
			signature = getRegistrationDTOFromSession().getDemographic(RegistrationConstants.INTRODUCER_SIGNATURE);
		}
		if(signature != null && !signature.isEmpty()) {	
				
			byte[] image=CryptoUtil.decodePlainBase64(signature);
			InputStream is = new ByteArrayInputStream(image);
			BufferedImage newBi = ImageIO.read(is);
			List<BufferedImage> list = new LinkedList<>();
			list.add(newBi);
			setScannedPages(list);
			return true;
		}
		if(documentDto == null) {
			this.scannedPages = new ArrayList<>();
			return false;
		}
		
		if (RegistrationConstants.PDF.equalsIgnoreCase(documentDto.getFormat())) {
			setScannedPages(DocScannerUtil.pdfToImages(documentDto.getDocument()));
			return true;
		} else {
			InputStream is = new ByteArrayInputStream(documentDto.getDocument());
			BufferedImage newBi = ImageIO.read(is);
			List<BufferedImage> list = new LinkedList<>();
			list.add(newBi);
			setScannedPages(list);
			return true;
		}
	}

	public void scanDocument(String fieldId, FxControl fxControl, boolean isPreviewOnly,String subType) {
		try {
			this.fxControl = fxControl;

			loadDataIntoScannedPages(fieldId,subType);

			initializeAndShowScanPopup(isPreviewOnly,subType);

			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Scan window displayed to scan and upload documents");
			return;

		} catch (IOException exception) {
			LOGGER.error(exception.getMessage() , exception);
		}

		generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOCUMENT_ERROR));
	}

	public String getSelectedScanDeviceName() {
		return selectedScanDeviceName;
	}

	public void setSelectedScanDeviceName(String selectedScanDeviceName) {
		this.selectedScanDeviceName = selectedScanDeviceName;
	}

	public FxControl getFxControl() {
		return fxControl;
	}

	public void setFxControl(FxControl fxControl) {
		this.fxControl = fxControl;
	}
	public BufferedImage saveSignature() throws Exception {
		String enabled = String.valueOf(ApplicationContext.map().get(RegistrationConstants.STUB_SCANNER_ENABLED));
		if (subType.equals(RegistrationConstants.PROOF_OF_SIGNATURE) || subType.equals(RegistrationConstants.PROOF_OF_INTRODUCER_SIGNATURE))
			try {
				{
					Optional<DocScanDevice> result = signatureFacade.getConnectedDevices(enabled).stream()
							.filter(d -> d.getId().equals(selectedScanDeviceName)).findFirst();
					if (result == null || !result.isPresent()) {
						LOGGER.error("No scan devices found");
						generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants
								.getMessageLanguageSpecific(RegistrationUIConstants.NO_DEVICE_FOUND));
						return null;
					}
					BufferedImage image = signatureFacade.scanDocument(result.get(),
							DeviceType.SIGNATURE_PAD.toString());
					signatureFacade.stopDevice(result.get());
					return image;
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		return null;
	}


}