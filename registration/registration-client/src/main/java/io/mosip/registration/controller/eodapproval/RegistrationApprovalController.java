package io.mosip.registration.controller.eodapproval;

import static io.mosip.registration.constants.LoggerConstants.LOG_REG_PENDING_APPROVAL;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;
import static io.mosip.registration.constants.RegistrationConstants.REG_UI_LOGIN_LOADER_EXCEPTION;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.ProcessNames;
import io.mosip.registration.constants.RegistrationClientStatusCode;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.vo.RegistrationApprovalVO;
import io.mosip.registration.dto.RegistrationApprovalDTO;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegBaseUncheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.service.packet.PacketHandlerService;
import io.mosip.registration.service.packet.RegistrationApprovalService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * {@code RegistrationApprovalController} is the controller class for
 * Registration approval.
 *
 * @author Mahesh Kumar
 */
@Controller
public class RegistrationApprovalController extends BaseController implements Initializable {

	/** Instance of {@link Logger}. */

	private Logger LOGGER = AppConfig.getLogger(RegistrationApprovalController.class);

	/** object for Registration approval service class. */

	@Autowired
	private RegistrationApprovalService registration;

	/** Table to display the created packets. */

	@FXML
	private TableView<RegistrationApprovalVO> table;

	/** Sl No column in the table. */

	@FXML
	private TableColumn<RegistrationApprovalVO, String> slno;
	
	/** Registration Id column in the table. */

	@FXML
	private TableColumn<RegistrationApprovalVO, String> id;

	/** Date column in the table. */

	@FXML
	private TableColumn<RegistrationApprovalVO, String> date;
	
	/** status comment column in the table. */
	@FXML
	private TableColumn<RegistrationApprovalVO, Image> statusComment;
	
	@FXML
	private TableColumn<RegistrationApprovalVO, String> operatorId;

	/** Acknowledgement form column in the table. */

	@FXML
	private TableColumn<RegistrationApprovalVO, String> acknowledgementFormPath;

	/** Button for approval. */

	@FXML
	private ToggleButton approvalBtn;

	/** Button for rejection. */

	@FXML
	private ToggleButton rejectionBtn;
	
	@FXML
	private ImageView approvalImageView;
	@FXML
	private ImageView rejectionImageView;
	
	@FXML
	private ImageView authenticateImageView;
	
	@FXML
	private ImageView exportImageView;

	/** Button for authentication. */

	@FXML
	private ToggleButton authenticateBtn;
	/** The image view. */
	@FXML
	private WebView webView;

	/** The approve registration root sub pane. */
	@FXML
	private GridPane approveRegistrationRootSubPane;

	/** The image anchor pane. */
	@FXML
	private GridPane imageAnchorPane;

	/** The map list. */
	private List<Map<String, String>> approvalmapList = null;

	/** object for registration approval service. */
	@Autowired
	private RegistrationApprovalService registrationApprovalService;

	/** object for rejection controller. */
	@Autowired
	private RejectionController rejectionController;

	/** object for finger print authentication controller. */
	@Autowired
	private EODAuthenticationController eodAuthenticationController;

	private Stage primaryStage;

	@FXML
	private TextField filterField;

	@Autowired
	private PacketHandlerService packetHandlerService;

	private ObservableList<RegistrationApprovalVO> observableList;

	private Map<String, Integer> packetIds = new HashMap<>();

	/**
	 * @return the primaryStage
	 */
	public Stage getPrimaryStage() {
		return primaryStage;
	}

	/**
	 * @param primaryStage the primaryStage to set
	 */
	public void setPrimaryStage(Stage primaryStage) {
		this.primaryStage = primaryStage;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see javafx.fxml.Initializable#initialize(java.net.URL,
	 * java.util.ResourceBundle)
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
				RegistrationConstants.ENABLE);

		setImage(exportImageView	, RegistrationConstants.EXPORT_ICON_IMG);
		setImage(authenticateImageView	, RegistrationConstants.AUTHENTICATE_IMG);
		setImage(rejectionImageView	, RegistrationConstants.REJECT_IMG);
		setImage(approvalImageView	, RegistrationConstants.APPROVE_IMG);
		
		rejectionBtn.hoverProperty().addListener((ov, oldValue, newValue) -> {
			if (newValue) {

				setImage(rejectionImageView	, RegistrationConstants.WRONG_IMG);
			} else {

				setImage(rejectionImageView	, RegistrationConstants.REJECT_IMG);
			}
		});
		
		reloadTableView();
		id.setResizable(false);
		statusComment.setResizable(false);
		operatorId.setResizable(false);
		disableColumnsReorder(table);
		table.getColumns().forEach(column -> column.setReorderable(false));
	}

	/**
	 * Method to reload table.
	 */
	private void reloadTableView() {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID, "Page loading has been started");
		approvalmapList = new ArrayList<>(5);
		authenticateBtn.setDisable(true);
		approvalBtn.setVisible(false);
		rejectionBtn.setVisible(false);
		imageAnchorPane.setVisible(false);
		filterField.clear();

		slno.setCellValueFactory(
				new PropertyValueFactory<RegistrationApprovalVO, String>(RegistrationConstants.EOD_PROCESS_SLNO));
		id.setCellValueFactory(
				new PropertyValueFactory<RegistrationApprovalVO, String>(RegistrationConstants.EOD_PROCESS_ID));
		date.setCellValueFactory(
				new PropertyValueFactory<RegistrationApprovalVO, String>(RegistrationConstants.EOD_PROCESS_DATE));
//		statusComment.setCellValueFactory(new PropertyValueFactory<RegistrationApprovalVO, Image>(
//				RegistrationConstants.EOD_PROCESS_STATUSCOMMENT));
		acknowledgementFormPath.setCellValueFactory(new PropertyValueFactory<RegistrationApprovalVO, String>(
				RegistrationConstants.EOD_PROCESS_ACKNOWLEDGEMENTFORMPATH));
		operatorId.setCellValueFactory(new PropertyValueFactory<RegistrationApprovalVO, String>(
				RegistrationConstants.OPERATOR_ID));
		
		statusComment.setCellFactory(param -> {
		       //Set up the ImageView
		       final ImageView imageview = new ImageView();
		       imageview.setFitHeight(30);
		       imageview.setFitWidth(30);

		       //Set up the Table
		       TableCell<RegistrationApprovalVO, Image> cell = new TableCell<RegistrationApprovalVO, Image>() {
		           public void updateItem(Image item, boolean empty) {
		             if (item != null) {
		            	 imageview.setImage(item);
		             } else {
		            	 imageview.setImage(null);
		             }
		           }
		        };
		        // Attach the imageview to the cell
		        cell.setGraphic(imageview);
		        return cell;
		});
		
		id.setCellFactory(param -> {
			// Set up the Table
			TableCell<RegistrationApprovalVO, String> cell = new TableCell<RegistrationApprovalVO, String>() {
				public void updateItem(String item, boolean empty) {
					if (!empty) {
						setText(item);
						int currentIndex = indexProperty().getValue() < 0 ? 0 : indexProperty().getValue();
						if (param.getTableView().getItems().get(currentIndex).getHasBwords()) {
							setStyle("-fx-background-color: yellow;");
						}
					}
					if (item == null) {
						setText(null);
						setStyle(null);
					}
				}
			};
			return cell;
		});
		
		statusComment.setCellValueFactory(new PropertyValueFactory<RegistrationApprovalVO, Image>(RegistrationConstants.EOD_PROCESS_STATUSCOMMENT));

		populateTable();

		table.getSelectionModel().selectFirst();

		if (table.getSelectionModel().getSelectedItem() != null) {
			viewAck();
		}

		table.setOnMouseClicked((MouseEvent event) -> {
			if (event.getClickCount() == 1) {
				viewAck();
			}
		});

		table.setOnKeyReleased(event -> {
			if (event.getCode() == KeyCode.UP || event.getCode() == KeyCode.DOWN) {
				viewAck();
			}
		});

		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID, "Page loading has been completed");
	}

	/**
	 * Viewing RegistrationAcknowledgement on selecting the Registration record.
	 */
	private void viewAck() {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Displaying the Acknowledgement form started");
		if (table.getSelectionModel().getSelectedItem() != null) {

			if (!approvalmapList.isEmpty()) {
				authenticateBtn.setDisable(false);
			}

			webView.getEngine().loadContent(RegistrationConstants.EMPTY);

			approvalBtn.setVisible(true);
			rejectionBtn.setVisible(true);
			imageAnchorPane.setVisible(true);

			try{
				String acknowledgementContent = packetHandlerService.getAcknowledgmentReceipt(table.getSelectionModel().getSelectedItem().getPacketId(),
						table.getSelectionModel().getSelectedItem().getAcknowledgementFormPath());
				webView.getEngine().loadContent(acknowledgementContent);
			} catch (RegBaseCheckedException | io.mosip.kernel.core.exception.IOException ex) {
				LOGGER.error("REGSITRATION_ACKNOWLEDGEMNT_PAGE_LOADING_FAILED", ex);
			}

		}
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Displaying the Acknowledgement form completed");
	}

	/**
	 * {@code populateTable} method is used for populating registration data.
	 * 
	 */
	private void populateTable() {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID, "table population has been started");
		try {
			List<RegistrationApprovalDTO> listData = null;
			List<RegistrationApprovalVO> registrationApprovalVO = new ArrayList<>();

			listData = registration.getEnrollmentByStatus(RegistrationClientStatusCode.CREATED.getCode());

			if (!listData.isEmpty()) {

				int count = 1;
				for (RegistrationApprovalDTO approvalDTO : listData) {
					registrationApprovalVO.add(
							new RegistrationApprovalVO("    " + count++, approvalDTO.getId(), approvalDTO.getPacketId(), approvalDTO.getDate(),
									approvalDTO.getAcknowledgementFormPath(), approvalDTO.getOperatorId(), null, approvalDTO.getHasBwords()));
				}
				int rowNum = 0;
				for (RegistrationApprovalDTO approvalDTO : listData) {
					packetIds.put(approvalDTO.getPacketId(), rowNum++);
				}

				// 1. Wrap the ObservableList in a FilteredList (initially display all data).
				observableList = FXCollections.observableArrayList(registrationApprovalVO);
				wrapListAndAddFiltering(observableList);
			} else {
				approveRegistrationRootSubPane.disableProperty().set(true);
				table.setPlaceholder(new Label(RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.PLACEHOLDER_LABEL)));
				if (observableList != null) {
					observableList.clear();
					wrapListAndAddFiltering(observableList);
				}
				filterField.clear();
			}
		} catch (RegBaseCheckedException regBaseCheckedException) {
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					regBaseCheckedException.getErrorText());
		}

		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID, "table population has been ended");
	}

	protected void wrapListAndAddFiltering(ObservableList<RegistrationApprovalVO> oList) {
		FilteredList<RegistrationApprovalVO> filteredData = new FilteredList<>(oList, p -> true);

		// 2. Set the filter Predicate whenever the filter changes.
		filterField.textProperty().addListener((observable, oldValue, newValue) -> 	filterData(newValue, filteredData));
		if (!filterField.getText().isEmpty()) {
			filterData(filterField.getText(), filteredData);
		}
		// 3. Wrap the FilteredList in a SortedList.
		SortedList<RegistrationApprovalVO> sortedList = new SortedList<>(filteredData);

		// 4. Bind the SortedList comparator to the TableView comparator.
		sortedList.comparatorProperty().bind(table.comparatorProperty());
		table.setItems(sortedList);
	}

	private void filterData(String newValue, FilteredList<RegistrationApprovalVO> filteredData) {
		filteredData.setPredicate(reg -> {
			// If filter text is empty, display all ID's.
			if (newValue == null || newValue.isEmpty()) {
				return true;
			}
			// Compare every ID with filter text.
			String lowerCaseFilter = newValue.toLowerCase();
			if (reg.getId().contains(lowerCaseFilter)) {
				// Filter matches first name.
				table.getSelectionModel().selectFirst();
				return true;
			}
			return false; // Does not match.
		});
		table.getSelectionModel().selectFirst();
		if (table.getSelectionModel().getSelectedItem() != null) {
			viewAck();
			approvalBtn.setDisable(false);
			rejectionBtn.setDisable(false);
		}else {
			webView.getEngine().loadContent(RegistrationConstants.EMPTY);
			approvalBtn.setDisable(true);
			rejectionBtn.setDisable(true);
		}
	}
	

	
	private int actionCounter=0;

	/**
	 * Opens the home page screen.
	 */
	public void goToHomePageFromApproval() {
		try {
			if (actionCounter > 0) {
				if (pageNavigantionAlert()) {
					BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
					if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
						clearOnboardData();
						clearRegistrationData();
					} else {
						SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
								RegistrationConstants.ENABLE);
					}
					actionCounter = 0;
				}
			} else {
				BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
				if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
					clearOnboardData();
					clearRegistrationData();
				} else {
					SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
							RegistrationConstants.ENABLE);
				}
				actionCounter = 0;
			}
		} catch (IOException ioException) {
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		} catch (RuntimeException runtimException) {
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					runtimException.getMessage() + ExceptionUtils.getStackTrace(runtimException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		}
	}
	
	/**
	 * {@code updateStatus} is to update the status of registration.
	 *
	 * @param event
	 *            the event
	 * @throws RegBaseCheckedException
	 *             the reg base checked exception
	 */
	public void updateStatus(ActionEvent event) throws RegBaseCheckedException {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Registration status updation has been started");
		
		SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
				RegistrationConstants.DISABLE);		
		actionCounter++;

		ToggleButton tBtn = (ToggleButton) event.getSource();
		Stage primarystage = null;

		if (tBtn.getId().equals(approvalBtn.getId())) {

			primarystage = new Stage();
			ApprovalController.initData(table.getSelectionModel().getSelectedItem(), packetIds, primarystage,
					approvalmapList, observableList, table,
					RegistrationConstants.EOD_PROCESS_REGISTRATIONAPPROVALCONTROLLER);

			loadStage(primarystage, RegistrationConstants.APPROVAL_PAGE);

			authenticateBtn.setDisable(false);

		} else {
			primarystage = new Stage();
			try {

				if (tBtn.getId().equals(rejectionBtn.getId())) {

					rejectionController.initData(table.getSelectionModel().getSelectedItem(), packetIds, primarystage,
							approvalmapList, observableList, table,
							RegistrationConstants.EOD_PROCESS_REGISTRATIONAPPROVALCONTROLLER);

					loadStage(primarystage, RegistrationConstants.REJECTION_PAGE);

					authenticateBtn.setDisable(false);

				} else if (tBtn.getId().equals(authenticateBtn.getId())) {
					SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
							RegistrationConstants.ENABLE);
					
					loadStage(primarystage, RegistrationConstants.USER_AUTHENTICATION);
					eodAuthenticationController.init(this, ProcessNames.EOD.getType());
				}

			} catch (RegBaseCheckedException checkedException) {
				primarystage.close();
				LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
						"No of Authentication modes is empty");

			} catch (RuntimeException runtimeException) {
				LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
						runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			}
		}
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Registration status updation has been ended");
	}

	/**
	 * Loading stage.
	 *
	 * @param primarystage
	 *            the stage
	 * @param fxmlPath
	 *            the fxml path
	 * @return the stage
	 * @throws RegBaseCheckedException
	 *             the reg base checked exception
	 */
	private Stage loadStage(Stage primarystage, String fxmlPath) throws RegBaseCheckedException {

		try {

			Pane authRoot = BaseController.load(getClass().getResource(fxmlPath));
			Scene scene = new Scene(authRoot);
			scene.getStylesheets().add(ClassLoader.getSystemClassLoader()
					.getResource(getCssName()).toExternalForm());
			primarystage.initStyle(StageStyle.UNDECORATED);
			primarystage.setScene(scene);
			primarystage.initModality(Modality.WINDOW_MODAL);
			primarystage.initOwner(fXComponents.getStage());
			primarystage.show();
			primarystage.resizableProperty().set(false);
			this.primaryStage = primarystage;

		} catch (IOException ioException) {
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));

			throw new RegBaseCheckedException(RegistrationExceptionConstants.REG_UI_LOGIN_IO_EXCEPTION.getErrorCode(),
					RegistrationExceptionConstants.REG_UI_LOGIN_IO_EXCEPTION.getErrorMessage(), ioException);
		} catch (RuntimeException runtimeException) {

			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));

			throw new RegBaseUncheckedException(REG_UI_LOGIN_LOADER_EXCEPTION, runtimeException.getMessage());
		}
		return primarystage;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.controller.BaseController#getFingerPrintStatus()
	 */
	@Override
	public void updateAuthenticationStatus() {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Updation of registration according to status started");
		try {

			List<String> regIds = new ArrayList<>();
			for (Map<String, String> map : approvalmapList) {
				registrationApprovalService.updateRegistrationWithPacketId(map.get(RegistrationConstants.PACKET_ID),
						map.get(RegistrationConstants.STATUSCOMMENT), map.get(RegistrationConstants.STATUSCODE));
				regIds.add(map.get(RegistrationConstants.REGISTRATIONID));
			}
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.AUTH_APPROVAL_SUCCESS_MSG));
			actionCounter = 0;
			primaryStage.close();
			reloadTableView();

			/*if (RegistrationAppHealthCheckUtil.isNetworkAvailable() && !regIds.isEmpty()) {

				uploadPacketsInBackground(regIds);

			}*/
		} catch(RegBaseCheckedException regBaseCheckedException) {
			
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					"unable to approve or reject packets" + regBaseCheckedException.getMessage()
							+ ExceptionUtils.getStackTrace(regBaseCheckedException));

			if(regBaseCheckedException.getErrorCode().equals(RegistrationExceptionConstants.AUTH_ADVICE_USR_ERROR.getErrorCode())) {
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.AUTH_ADVICE_FAILURE));
			}
		}  catch (RuntimeException runtimeException) {
			LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
					"unable to sync and upload of packets" + runtimeException.getMessage()
							+ ExceptionUtils.getStackTrace(runtimeException));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.AUTH_FAILURE));
		}
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Updation of registration according to status ended");
	}


	/**
	 * Export data.
	 */
	public void exportData() {
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Exporting Registration status details has been started");

		String str = filterField.getText();
		Stage stage = new Stage();
		DirectoryChooser destinationSelector = new DirectoryChooser();
		destinationSelector.setTitle(RegistrationConstants.FILE_EXPLORER_NAME);
		Path currentRelativePath = Paths.get("");
		File defaultDirectory = new File(currentRelativePath.toAbsolutePath().toString());
		destinationSelector.setInitialDirectory(defaultDirectory);
		File destinationPath = destinationSelector.showDialog(stage);
		if (destinationPath != null) {

			filterField.clear();
			String fileData = table.getItems().stream()
					.map(approvaldto -> approvaldto.getSlno().trim().concat(RegistrationConstants.COMMA).concat("'")
							.concat(approvaldto.getId()).concat("'").concat(RegistrationConstants.COMMA).concat("'")
							.concat(approvaldto.getDate()).concat("'").concat(RegistrationConstants.COMMA)
							.concat(approvaldto.getOperatorId()).concat("'").concat(RegistrationConstants.COMMA)
							.concat(approvaldto.getStatusComment().getUrl()))
					.collect(Collectors.joining(RegistrationConstants.NEW_LINE));
			String headers = RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_SLNO_LABEL).concat(RegistrationConstants.COMMA)
					.concat(RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_REGISTRATIONID_LABEL)).concat(RegistrationConstants.COMMA)
					.concat(RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_REGISTRATIONDATE_LABEL)).concat(RegistrationConstants.COMMA)
					.concat(RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_OPERATORID_LABEL)).concat(RegistrationConstants.COMMA)
					.concat(RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_STATUS_LABEL)).concat(RegistrationConstants.NEW_LINE);
			fileData = headers + fileData;
			filterField.setText(str);
			try (Writer writer = new BufferedWriter(new FileWriter(destinationPath + "/"
					+ RegistrationConstants.EXPORT_FILE_NAME.concat(RegistrationConstants.UNDER_SCORE)
							.concat(getcurrentTimeStamp()).concat(RegistrationConstants.EXPORT_FILE_TYPE)))) {
				writer.write(fileData);

				generateAlert(RegistrationConstants.ALERT_INFORMATION,
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_DETAILS_EXPORT_SUCCESS));

			} catch (IOException ioException) {
				LOGGER.error(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
						ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));

				generateAlert(RegistrationConstants.ALERT_INFORMATION,
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.EOD_DETAILS_EXPORT_FAILURE));

			}
		}
		LOGGER.info(LOG_REG_PENDING_APPROVAL, APPLICATION_NAME, APPLICATION_ID,
				"Exporting Registration status details has been ended");
	}
	
	/**
	 * This method gets the current timestamp in yyyymmddhhmmss format.
	 * 
	 * @return current timestamp in fourteen digits
	 */
	private String getcurrentTimeStamp() {
		DateTimeFormatter format = DateTimeFormatter
				.ofPattern(RegistrationConstants.EOD_PROCESS_DATE_FORMAT_FOR_FILE);
		return LocalDateTime.now().format(format);
	}

}