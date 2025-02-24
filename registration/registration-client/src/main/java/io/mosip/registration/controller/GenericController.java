package io.mosip.registration.controller;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.registration.controller.reg.LanguageSelectionController;
import io.mosip.registration.dto.*;
import io.mosip.registration.dto.schema.ValuesDTO;
import io.mosip.registration.enums.FlowType;
import io.mosip.registration.util.control.impl.*;
import io.mosip.registration.validator.RequiredFieldValidator;
import javafx.beans.binding.Bindings;
import javafx.fxml.Initializable;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

import static io.mosip.registration.constants.RegistrationConstants.EMPTY;
import static io.mosip.registration.constants.RegistrationConstants.HASH;
import static io.mosip.registration.constants.RegistrationConstants.REG_AUTH_PAGE;
import static io.mosip.registration.constants.RegistrationUIConstants.DEMOGRAPHIC_DETAILS;
import static io.mosip.registration.constants.RegistrationUIConstants.DOCUMENT_UPLOAD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import io.mosip.kernel.core.idvalidator.exception.InvalidIDException;
import io.mosip.kernel.core.idvalidator.spi.PridValidator;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.ProcessNames;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.auth.AuthenticationController;
import io.mosip.registration.controller.reg.RegistrationPreviewController;
import io.mosip.registration.dao.MasterSyncDao;
import io.mosip.registration.dto.payments.CheckPRNInTransLogsResponseDTO;
import io.mosip.registration.dto.payments.CheckPRNStatusResponseDTO;
import io.mosip.registration.dto.payments.ConsumePRNResponseDTO;
import io.mosip.registration.dto.payments.PRNVerificationResponse;
import io.mosip.registration.dto.schema.ProcessSpecDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.dto.schema.UiScreenDTO;
import io.mosip.registration.entity.LocationHierarchy;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.service.payments.PrnService;
import io.mosip.registration.service.sync.PreRegistrationDataSyncService;
import io.mosip.registration.util.control.FxControl;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.SneakyThrows;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import io.mosip.registration.controller.BaseController;

/**
 * {@code GenericController} is to capture the demographic/demo/Biometric
 * details
 *
 * @author YASWANTH S
 * @since 1.0.0
 *
 */

@Controller
public class GenericController extends BaseController {

	protected static final Logger LOGGER = AppConfig.getLogger(GenericController.class);

	private static final String TAB_LABEL_ERROR_CLASS = "tabErrorLabel";
	private static final String LABEL_CLASS = "additionaInfoReqIdLabel";
	private static final String NAV_LABEL_CLASS = "navigationLabel";
	private static final String TEXTFIELD_CLASS = "preregFetchBtnStyle";
	private static final String CONTROLTYPE_TEXTFIELD = "textbox";
	private static final String CONTROLTYPE_BIOMETRICS = "biometrics";
	private static final String CONTROLTYPE_DOCUMENTS = "fileupload";
	private static final String CONTROLTYPE_DROPDOWN = "dropdown";
	private static final String CONTROLTYPE_CHECKBOX = "checkbox";
	private static final String CONTROLTYPE_BUTTON = "button";
	private static final String CONTROLTYPE_DOB = "date";
	private static final String CONTROLTYPE_DOB_AGE = "ageDate";
	private static final String CONTROLTYPE_HTML = "html";
	private static final String CONTROLTYPE_COMMENT = "comment";
	private static final String CONTROLTYPE_TITLE = "title";
	private static final String CONTROLTYPE_TOGGLE_BUTTON = "toggleButton";
	private ProcessSpecDto process;

	/**
	 * Top most Grid pane in FXML
	 */
	@FXML
	private GridPane genericScreen;

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private AnchorPane navigationAnchorPane;

	@FXML
	private Button next;

	@FXML
	private Button authenticate;

	@FXML
	private Label notification;

	private ProgressIndicator progressIndicator;

	@Autowired
	private AuthenticationController authenticationController;

	@Autowired
	private MasterSyncDao masterSyncDao;

	@Autowired
	private RegistrationPreviewController registrationPreviewController;

	@Autowired
	private PridValidator<String> pridValidatorImpl;

	@Autowired
	private PreRegistrationDataSyncService preRegistrationDataSyncService;

	@Autowired
	private PrnService prnService;

	@Value("${nira.payment.gateway.statusCode}")
	private String statusCode;

	private boolean isPrnValid = false;

	private final Map<Node, Label> nodePrnLabelMap = new HashMap<>();

	private static TreeMap<Integer, UiScreenDTO> orderedScreens = new TreeMap<>();
	private static Map<String, FxControl> fxControlMap = new HashMap<String, FxControl>();
	private Stage keyboardStage;
	private boolean preregFetching = false;
	private boolean keyboardVisible = false;
	private String previousId;
	private Integer additionalInfoReqIdScreenOrder = null;
	public static Map<String, TreeMap<Integer, List<String>>> hierarchyLevels = new HashMap<String, TreeMap<Integer, List<String>>>();
	public static Map<String, TreeMap<Integer, List<String>>> currentHierarchyMap = new HashMap<String, TreeMap<Integer, List<String>>>();
	public static List<UiFieldDTO> fields = new ArrayList<>();
	private LanguageSelectionController registrationDTO;
	private RequiredFieldValidator requiredFieldValidator;

	public static Map<String, FxControl> getFxControlMap() {
		return fxControlMap;
	}

	// @cifu
	private TextField registrationNumberTextField;
	@Autowired
	private QrCodePopUpViewController qrCodePopUpViewController;
	List<String> updateFlowAllowedProcess = new ArrayList<>();

	public TextField getRegistrationNumberTextField() {
		return registrationNumberTextField;
	}// @cifu

	public void disableAuthenticateButton(boolean disable) {
		authenticate.setDisable(disable);
	}

	private void initialize(RegistrationDTO registrationDTO) {
		orderedScreens.clear();
		fxControlMap.clear();
		hierarchyLevels.clear();
		currentHierarchyMap.clear();
		fillHierarchicalLevelsByLanguage();
		anchorPane.prefWidthProperty().bind(genericScreen.widthProperty());
		anchorPane.prefHeightProperty().bind(genericScreen.heightProperty());
		fields = getAllFields(registrationDTO.getProcessId(), registrationDTO.getIdSchemaVersion());
		additionalInfoReqIdScreenOrder = null;
		updateFlowAllowedProcess.clear();
		updateFlowAllowedProcess.add(FlowType.UPDATE.name());
		updateFlowAllowedProcess.add(FlowType.RENEWAL.name());
		updateFlowAllowedProcess.add(FlowType.FIRSTID.name());
	}

	private void fillHierarchicalLevelsByLanguage() {
		for (String langCode : getConfiguredLangCodes()) {
			TreeMap<Integer, List<String>> hierarchicalData = new TreeMap<>();
			List<LocationHierarchy> hierarchies = masterSyncDao.getAllLocationHierarchy(langCode);
			hierarchies.forEach(hierarchy -> {
				hierarchicalData.computeIfAbsent(hierarchy.getHierarchyLevel(), k -> new ArrayList<>())
						.add(hierarchy.getHierarchyLevelName());
			});
			hierarchyLevels.put(langCode, hierarchicalData);
		}
	}

	private HBox getPreRegistrationFetchComponent(String processFlow) {
		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

		HBox hBox = new HBox();
		hBox.setAlignment(Pos.CENTER_LEFT);
		hBox.setSpacing(20);
		hBox.setPrefHeight(100);
		hBox.setPrefWidth(200);

		HBox innerHBox = new HBox();
		innerHBox.setAlignment(Pos.CENTER_LEFT);
		innerHBox.setSpacing(0);
		innerHBox.setPrefHeight(100);

		Label label = new Label();
		label.getStyleClass().add(LABEL_CLASS);
		label.setId("preRegistrationLabel");
		label.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("search_for_Pre_registration_id"));
		hBox.getChildren().add(label);
		TextField textField = new TextField();
		textField.setId("preRegistrationId");
		textField.getStyleClass().add(TEXTFIELD_CLASS);
		hBox.getChildren().add(textField);
		this.registrationNumberTextField = textField;

		Button button = new Button();
		button.setId("fetchBtn");
		button.getStyleClass().add("demoGraphicPaneContentButton");
		button.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS).getString("fetch"));

		Button scanQRbutton = new Button();
		scanQRbutton.setId("scanQRBtn");
		scanQRbutton.setGraphic(new ImageView(
				new Image(this.getClass().getResourceAsStream("/images/qr-code.png"), 25, 25, true, true)));
		scanQRbutton.getStyleClass().add("demoGraphicPaneContentButton");
		scanQRbutton.setOnAction(event -> {
			executeQRCodeScan();
		});
		innerHBox.getChildren().add(scanQRbutton);
		innerHBox.getChildren().add(textField);

		button.setOnAction(event -> {
			executePreRegFetchTask(textField, processFlow);
		});

		hBox.getChildren().add(innerHBox);
		hBox.getChildren().add(button);
		progressIndicator = new ProgressIndicator();
		progressIndicator.setId("progressIndicator");
		progressIndicator.setVisible(false);
		hBox.getChildren().add(progressIndicator);
		return hBox;
	}

	void executePreRegFetchTask(TextField textField, String processFlow) {
		preregFetching = true;
		genericScreen.setDisable(true);
		progressIndicator.setVisible(true);

		Service<Void> taskService = new Service<Void>() {
			@Override
			protected Task<Void> createTask() {
				return new Task<Void>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected Void call() {
						Platform.runLater(() -> {
							boolean isValid = false;
							try {
								isValid = pridValidatorImpl.validateId(textField.getText());
							} catch (InvalidIDException invalidIDException) {
								isValid = false;
							}

							if (!isValid) {
								generateAlertLanguageSpecific(RegistrationConstants.ERROR,
										RegistrationUIConstants.PRE_REG_ID_NOT_VALID);
								return;
							}
							ResponseDTO responseDTO = preRegistrationDataSyncService
									.getPreRegistration(textField.getText(), false);

							if (responseDTO.getErrorResponseDTOs() != null
									&& !responseDTO.getErrorResponseDTOs().isEmpty()
									&& responseDTO.getErrorResponseDTOs().get(0).getMessage() != null
									&& responseDTO.getErrorResponseDTOs().get(0).getMessage()
									.equalsIgnoreCase(RegistrationConstants.CONSUMED_PRID_ERROR_CODE)) {
								generateAlertLanguageSpecific(RegistrationConstants.ERROR,
										RegistrationConstants.PRE_REG_CONSUMED_PACKET_ERROR);
								return;
							}

							if (responseDTO.getSuccessResponseDTO() != null) {
								String preRegType = (String) responseDTO.getSuccessResponseDTO().getOtherAttributes()
										.get("preRegType");
								getRegistrationDTOFromSession().setPreRegType(preRegType);

								if (processFlow.equals(FlowType.UPDATE.name())
										? !updateFlowAllowedProcess.contains(preRegType)
										: !processFlow.equals(preRegType)) {
									generateAlertLanguageSpecific(RegistrationConstants.ERROR,
											RegistrationConstants.PRE_REG_WRONG_PROCESS_FLOW);
									return;
								}
							}

							try {
								loadPreRegSync(responseDTO);
								resetValue();
								if (responseDTO.getSuccessResponseDTO() != null) {
									getRegistrationDTOFromSession().setPreRegistrationId(textField.getText());
									getRegistrationDTOFromSession().setAppId(textField.getText());
									TabPane tabPane = (TabPane) anchorPane
											.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
									tabPane.setId(textField.getText());
									getRegistrationDTOFromSession().setRegistrationId(textField.getText());
								}
							} catch (RegBaseCheckedException exception) {
								generateAlertLanguageSpecific(RegistrationConstants.ERROR,
										RegistrationConstants.PRE_REG_TO_GET_PACKET_ERROR);
							}
						});
						return null;
					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				preregFetching = false;
				genericScreen.setDisable(false);
				progressIndicator.setVisible(false);
			}
		});
		taskService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				preregFetching = false;
				LOGGER.debug("Pre Registration Fetch failed");
				genericScreen.setDisable(false);
				progressIndicator.setVisible(false);
			}
		});
	}

	private void executeQRCodeScan() {
		genericScreen.setDisable(true);
		Service<Void> taskService = new Service<Void>() {
			@Override
			protected Task<Void> createTask() {
				return new Task<Void>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected Void call() {
						Platform.runLater(() -> {
							qrCodePopUpViewController.init("Scan QR Code");
						});
						return null;
					}
				};
			}
		};
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				genericScreen.setDisable(false);
			}
		});
		taskService.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				LOGGER.debug("QR code scan failed");
				genericScreen.setDisable(false);
			}
		});
	}

	private HBox getAdditionalInfoRequestIdComponent() {
		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);
		HBox hBox = new HBox();
		hBox.setAlignment(Pos.CENTER_LEFT);
		hBox.setSpacing(20);
		hBox.setPrefHeight(100);
		hBox.setPrefWidth(200);
		Label label = new Label();
		label.getStyleClass().add(LABEL_CLASS);
		label.setId("additionalInfoRequestIdLabel");
		label.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString("additionalInfoRequestId"));
		hBox.getChildren().add(label);
		TextField textField = new TextField();
		textField.setId("additionalInfoRequestId");
		textField.getStyleClass().add(TEXTFIELD_CLASS);
		hBox.getChildren().add(textField);

		textField.textProperty().addListener((observable, oldValue, newValue) -> {
			getRegistrationDTOFromSession().setAdditionalInfoReqId(newValue);
			getRegistrationDTOFromSession().setAppId(newValue.split("-")[0]);
			TabPane tabPane = (TabPane) anchorPane.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
			tabPane.setId(getRegistrationDTOFromSession().getAppId());
			getRegistrationDTOFromSession().setRegistrationId(getRegistrationDTOFromSession().getAppId());
		});

		return hBox;
	}

	private boolean isAdditionalInfoRequestIdProvided(UiScreenDTO screenDTO) {
		Node node = anchorPane.lookup("#additionalInfoRequestId");
		if (node == null) {
			LOGGER.debug("#additionalInfoRequestId component is not created!");
			return true; // as the element is not present, it's either not required / enabled.
		}

		TextField textField = (TextField) node;
		boolean provided = (textField.getText() != null && !textField.getText().isBlank());

		if (screenDTO.getOrder() < additionalInfoReqIdScreenOrder)
			return true; // bypass check as current screen order is less than the screen it is displayed
		// in.

		if (!provided) {
			showHideErrorNotification(ApplicationContext
					.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
					.getString(RegistrationUIConstants.ADDITIONAL_INFO_REQ_ID_MISSING),null);
		}
		return provided;
	}

	private void loadPreRegSync(ResponseDTO responseDTO) throws RegBaseCheckedException {
		auditFactory.audit(AuditEvent.REG_DEMO_PRE_REG_DATA_FETCH, Components.REG_DEMO_DETAILS, SessionContext.userId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		SuccessResponseDTO successResponseDTO = responseDTO.getSuccessResponseDTO();
		List<ErrorResponseDTO> errorResponseDTOList = responseDTO.getErrorResponseDTOs();

		if (errorResponseDTOList != null && !errorResponseDTOList.isEmpty() || successResponseDTO == null
				|| successResponseDTO.getOtherAttributes() == null
				|| !successResponseDTO.getOtherAttributes().containsKey(RegistrationConstants.REGISTRATION_DTO)) {
			throw new RegBaseCheckedException(RegistrationExceptionConstants.PRE_REG_SYNC_FAIL.getErrorCode(),
					RegistrationExceptionConstants.PRE_REG_SYNC_FAIL.getErrorMessage());
		}
		Map<String, Object> demographics = getRegistrationDTOFromSession().getDemographics();
		for (UiScreenDTO screenDTO : orderedScreens.values()) {
			for (UiFieldDTO field : screenDTO.getFields()) {
				FxControl fxControl = getFxControl(field.getId());
				if (fxControl != null) {
					switch (fxControl.getUiSchemaDTO().getType()) {
						case "biometricsType":
							break;
						case "documentType":
							fxControl.selectAndSet(getRegistrationDTOFromSession().getDocuments().get(field.getId()));
							var document = getRegistrationDTOFromSession().getDocuments().get(field.getId());
							if (document != null && "pdf".equals(document.getFormat())) {
								try {
									PDDocument pdfDoc = PDDocument.load(document.getDocument());
									PDFRenderer pdfRenderer = new PDFRenderer(pdfDoc);
									List<BufferedImage> list = new LinkedList<>();

									for (int page = 0; page < pdfDoc.getNumberOfPages(); page++) {
										BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300); // Convert PDF to image with 300 DPI
										list.add(image);
									}

									fxControl.setData(list);
									document.setFormat("pdf");
								} catch (IOException e) {
									LOGGER.error("Buffered images conversion failed : {}", e);
								}
							}
							else if (document != null && !"pdf".equals(document.getFormat())) {
								try (InputStream is = new ByteArrayInputStream(document.getDocument())) {
									BufferedImage image = ImageIO.read(is);
									List<BufferedImage> list = new LinkedList<>();
									if (image != null) {
										if ("png".equalsIgnoreCase(document.getFormat())) {
											BufferedImage jpgImage = new BufferedImage(
													image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
											jpgImage.createGraphics().drawImage(image, 0, 0, Color.WHITE, null);
											list.add(jpgImage);
											fxControl.setData(list);
										} else {
											list.add(image);
											fxControl.setData(list);
										}
										document.setFormat("pdf");
									}
								} catch (IOException e) {
									LOGGER.error("Image conversion failed: {}", e);
								}
							}
							break;
						default:

							var demographicsCopy = (Map<String, Object>) SessionContext.map().get(RegistrationConstants.REGISTRATION_DATA_DEMO);
							fxControl.selectAndSet(getRegistrationDTOFromSession().getDemographics().get(field.getId()) != null ? getRegistrationDTOFromSession().getDemographics().get(field.getId()) : demographicsCopy.get(field.getId()));
//it will read data from field components and set it in registrationDTO along with selectedCodes and ageGroups
//kind of supporting data
							fxControl.setData(getRegistrationDTOFromSession().getDemographics().get(field.getId()) != null
									? getRegistrationDTOFromSession().getDemographics().get(field.getId())
									: demographicsCopy.get(field.getId()));

							break;
					}
				}
			}
		}
	}

	private void getScreens(List<UiScreenDTO> screenDTOS) {
		screenDTOS.forEach(dto -> {
			orderedScreens.put(dto.getOrder(), dto);
		});
	}

	private Map<String, List<UiFieldDTO>> getFieldsBasedOnAlignmentGroup(List<UiFieldDTO> screenFields) {
		Map<String, List<UiFieldDTO>> groupedScreenFields = new LinkedHashMap<>();
		if (screenFields == null || screenFields.isEmpty())
			return groupedScreenFields;

		// Applies only during Update flow
		if (getRegistrationDTOFromSession().getUpdatableFieldGroups() != null) {
			screenFields = screenFields.stream().filter(f -> f.getGroup() != null
							&& (getRegistrationDTOFromSession().getUpdatableFieldGroups().contains(f.getGroup())
							|| getRegistrationDTOFromSession().getDefaultUpdatableFieldGroups().contains(f.getGroup())))
					.collect(Collectors.toList());
			screenFields.forEach(f -> {
				getRegistrationDTOFromSession().getUpdatableFields().add(f.getId());
			});
		}

		screenFields.forEach(field -> {
			String alignmentGroup = field.getAlignmentGroup() == null ? field.getId() + "TemplateGroup"
					: field.getAlignmentGroup();

			if (field.isInputRequired()) {
				if (!groupedScreenFields.containsKey(alignmentGroup))
					groupedScreenFields.put(alignmentGroup, new LinkedList<UiFieldDTO>());

				groupedScreenFields.get(alignmentGroup).add(field);
			}
		});
		return groupedScreenFields;
	}

	private GridPane getScreenGridPane(String screenName) {
		GridPane gridPane = new GridPane();
		gridPane.setId(screenName);
		RowConstraints topRowConstraints = new RowConstraints();
		topRowConstraints.setPercentHeight(2);
		RowConstraints midRowConstraints = new RowConstraints();
		midRowConstraints.setPercentHeight(96);
		RowConstraints bottomRowConstraints = new RowConstraints();
		bottomRowConstraints.setPercentHeight(2);
		gridPane.getRowConstraints().addAll(topRowConstraints, midRowConstraints, bottomRowConstraints);

		ColumnConstraints columnConstraint1 = new ColumnConstraints();
		columnConstraint1.setPercentWidth(5);
		ColumnConstraints columnConstraint2 = new ColumnConstraints();
		columnConstraint2.setPercentWidth(90);
		ColumnConstraints columnConstraint3 = new ColumnConstraints();
		columnConstraint3.setPercentWidth(5);

		gridPane.getColumnConstraints().addAll(columnConstraint1, columnConstraint2, columnConstraint3);

		return gridPane;
	}

	private GridPane getScreenGroupGridPane(String id, GridPane screenGridPane) {
		GridPane groupGridPane = new GridPane();
		groupGridPane.setId(id);
		groupGridPane.prefWidthProperty().bind(screenGridPane.widthProperty());
		groupGridPane.getColumnConstraints().clear();
		ColumnConstraints columnConstraint = new ColumnConstraints();
		columnConstraint.setPercentWidth(100);
		groupGridPane.getColumnConstraints().add(columnConstraint);
		groupGridPane.setHgap(20);
		groupGridPane.setVgap(20);
		return groupGridPane;
	}

	private void addNavigationButtons(ProcessSpecDto processSpecDto) {

		Label navigationLabel = new Label();
		navigationLabel.getStyleClass().add(NAV_LABEL_CLASS);
		navigationLabel.setText(processSpecDto.getLabel().get(ApplicationContext.applicationLanguage()));
		navigationLabel.prefWidthProperty().bind(navigationAnchorPane.widthProperty());
		navigationLabel.setWrapText(true);

		navigationAnchorPane.getChildren().add(navigationLabel);
		AnchorPane.setTopAnchor(navigationLabel, 5.0);
		AnchorPane.setLeftAnchor(navigationLabel, 10.0);

		next.setOnAction(getNextActionHandler());
		authenticate.setOnAction(getRegistrationAuthActionHandler());
	}

	/*
	 * private String getScreenName(Tab tab) { return tab.getId().replace("_tab",
	 * EMPTY); }
	 */

	private boolean refreshScreenVisibility(String screenName) {
		boolean atLeastOneVisible = true;
		Optional<UiScreenDTO> screenDTO = orderedScreens.values().stream()
				.filter(screen -> screen.getName().equals(screenName)).findFirst();

		if (screenDTO.isPresent()) {
			LOGGER.info("Refreshing Screen: {}", screenName);
			screenDTO.get().getFields().forEach(field -> {
				FxControl fxControl = getFxControl(field.getId());
				if (fxControl != null)
					fxControl.refresh();
			});

			atLeastOneVisible = screenDTO.get().getFields().stream().anyMatch(
					field -> getFxControl(field.getId()) != null && getFxControl(field.getId()).getNode().isVisible());
		}
		LOGGER.info("Screen refreshed, Screen: {} visible : {}", screenName, atLeastOneVisible);
		return atLeastOneVisible;
	}

	private boolean refreshScreenVisibilityForDependentFields(String screenName, List<String> dependentFields) {
		boolean atLeastOneVisible = true;
		Optional<UiScreenDTO> screenDTO = orderedScreens.values()
				.stream()
				.filter(screen -> screen.getName().equals(screenName))
				.findFirst();

		if (screenDTO.isPresent()) {
			LOGGER.info("Refreshing Screen: {}", screenName);
			screenDTO.get().getFields().forEach(field -> {
				if (dependentFields.contains(field.getId())) {
					FxControl fxControl = getFxControl(field.getId());
					if (fxControl != null)
						fxControl.refreshDependentFields();
				}
			});

			atLeastOneVisible = screenDTO.get()
					.getFields()
					.stream()
					.anyMatch(field -> getFxControl(field.getId()) != null && getFxControl(field.getId()).getNode().isVisible());
		}
		LOGGER.info("Screen refreshed, Screen: {} visible : {}", screenName, atLeastOneVisible);
		return atLeastOneVisible;
	}

	private EventHandler getNextActionHandler() {
		return new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				TabPane tabPane = (TabPane) anchorPane
						.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
				int selectedIndex = tabPane.getSelectionModel().getSelectedIndex();
				while (selectedIndex < tabPane.getTabs().size()) {
					selectedIndex++;
					String newScreenName = tabPane.getTabs().get(selectedIndex).getId().replace("_tab", EMPTY);
					tabPane.getTabs().get(selectedIndex).setDisable(!refreshScreenVisibility(newScreenName));
					if (!tabPane.getTabs().get(selectedIndex).isDisabled()) {
						tabPane.getSelectionModel().select(selectedIndex);
						break;
					}
				}
			}
		};
	}

	private EventHandler getRegistrationAuthActionHandler() {
		return new EventHandler<ActionEvent>() {
			@SneakyThrows
			@Override
			public void handle(ActionEvent event) {
				TabPane tabPane = (TabPane) anchorPane
						.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
				String incompleteScreen = getInvalidScreenName(tabPane);

				if (incompleteScreen == null) {
					generateAlert(RegistrationConstants.ERROR, incompleteScreen + " Screen with ERROR !");
					return;
				}
				authenticationController.goToNextPage();
			}
		};
	}

	private void setTabSelectionChangeEventHandler(TabPane tabPane) {
		final boolean[] ignoreChange = {false};
		tabPane.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (ignoreChange[0]) {
					ignoreChange[0] = false;
					return;
				}

				LOGGER.debug("Old selection : {} New Selection : {}", oldValue, newValue);

				if (isKeyboardVisible() && keyboardStage != null) {
					keyboardStage.close();
				}

				// Check if we are moving to the previous tab (oldValue > newValue)
				boolean isMovingForward = newValue.intValue() > oldValue.intValue();

				if (oldValue.intValue() >= 0 && newValue.intValue() != oldValue.intValue()) {

					// Only validate the current screen if moving forward (to the next tab)
					if (isMovingForward) {
						// Validate the current screen using isScreenValid()
						boolean isValid = isScreenValid(tabPane.getTabs().get(oldValue.intValue()).getId());
						// If validation fails, prevent the tab change
						if (!isValid) {
							LOGGER.error("Current screen is not fully valid: {}", oldValue.intValue());

							// Prevent the tab change
							ignoreChange[0] = true;
							tabPane.getSelectionModel().select(oldValue.intValue());

							// Since showHideErrorNotification is called inside isScreenValid, no need to
							// call it again here
							return;
						}
						String oldTabName = tabPane.getTabs().get(oldValue.intValue()).getText();

						if (DEMOGRAPHIC_DETAILS.equals(oldTabName) || DOCUMENT_UPLOAD.equals(oldTabName)) {
							// Prevent the tab from changing immediately
							ignoreChange[0] = true;
							tabPane.getSelectionModel().select(oldValue.intValue());

							// Create the confirmation dialog
							Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
							confirmationDialog.setTitle("Confirmation Required");
							confirmationDialog.setHeaderText(null);
							confirmationDialog.setContentText("Please review your details before proceeding to the next section.");

							Stage dialogStage = (Stage) confirmationDialog.getDialogPane().getScene().getWindow();
							dialogStage.initModality(Modality.APPLICATION_MODAL);
							dialogStage.initStyle(StageStyle.UTILITY);

							DialogPane dialogPane = confirmationDialog.getDialogPane();

							// Centering the text
							Node contentLabel = dialogPane.lookup(".content.label");
							if (contentLabel != null) {
								contentLabel.setStyle("-fx-text-alignment: left; -fx-font-size: 14px;");
							}

							// Adding buttons to the dialog
							ButtonType proceedButton = new ButtonType("Proceed", ButtonBar.ButtonData.OK_DONE);
							ButtonType reviewButton = new ButtonType("Review Details", ButtonBar.ButtonData.CANCEL_CLOSE);
							confirmationDialog.getButtonTypes().setAll(proceedButton, reviewButton);

							// Customizing the button styles
							Button proceedButtonNode = (Button) dialogPane.lookupButton(proceedButton);
							if (proceedButtonNode != null) {
								proceedButtonNode.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-weight: bold;");
							}

							Button reviewButtonNode = (Button) dialogPane.lookupButton(reviewButton);
							if (reviewButtonNode != null) {
								reviewButtonNode.setStyle("-fx-text-fill: black;");
							}

							// Aligning buttons to the center
							Node buttonBar = dialogPane.lookup(".button-bar");
							if (buttonBar != null) {
								buttonBar.setStyle("-fx-alignment: center;");
							}

							tabPane.toFront();

							// Defer the dialog display to ensure TabPane is rendered first
							Platform.runLater(() -> {
								// Adding focus lost event to close the dialog if the window loses focus
								dialogStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
									if (!isNowFocused) {
										confirmationDialog.close(); // Close the dialog if the application loses focus
									}
								});

								Optional<ButtonType> result = confirmationDialog.showAndWait();
								if (result.isPresent() && result.get() == proceedButton) {
									// If "Proceed" is clicked, proceed to change the tab
									ignoreChange[0] = true;
									int newSelection = newValue.intValue() < 0 ? 0 : newValue.intValue();
									final String newScreenName = tabPane.getTabs()
											.get(newSelection)
											.getId()
											.replace("_tab", EMPTY);
									tabPane.getTabs()
											.get(newSelection)
											.setDisable(!refreshScreenVisibility(newScreenName));
									tabPane.getSelectionModel().select(newValue.intValue());
									showHideGeneralNotification(null);
								} else {
									// If "Review Details" is clicked, remain on the current tab
									ignoreChange[0] = false;
									tabPane.getSelectionModel().select(oldValue.intValue());
								}
							});

							return;
						}
					}
				}

				// Continue with the tab selection logic
				int newSelection = newValue.intValue() < 0 ? 0 : newValue.intValue();
				final String newScreenName = tabPane.getTabs().get(newSelection).getId().replace("_tab", EMPTY);

				// Hide continue button in preview page
				next.setVisible(!newScreenName.equals("AUTH"));
				authenticate.setVisible(newScreenName.equals("AUTH"));

				if (oldValue.intValue() < 0) {
					tabPane.getSelectionModel().selectFirst();
					return;
				}

				// request to load Preview / Auth page, allowed only when no errors are found in visible screens
				if ((newScreenName.equals("AUTH") || newScreenName.equals("PREVIEW"))) {
					String invalidScreenName = getInvalidScreenName(tabPane);
					if (invalidScreenName.equals(EMPTY)) {
						notification.setVisible(false);
						loadPreviewOrAuthScreen(tabPane, tabPane.getTabs().get(newValue.intValue()));
						return;
					} else {
						tabPane.getSelectionModel().select(oldValue.intValue());
						return;
					}
				}

				// Refresh screen visibility
				tabPane.getTabs().get(newSelection).setDisable(!refreshScreenVisibility(newScreenName));
				boolean isSelectedDisabledTab = tabPane.getTabs().get(newSelection).isDisabled();

				// selecting disabled tab, take no action, stay in the same screen
				if (isSelectedDisabledTab) {
					tabPane.getSelectionModel().select(oldValue.intValue());
					return;
				}

				// traversing back is allowed without a need to validate current / next screen
				if (oldValue.intValue() > newSelection) {
					tabPane.getSelectionModel().select(newValue.intValue());
					return;
				}

				// traversing forward is always one step next
				if (!isScreenValid(tabPane.getTabs().get(oldValue.intValue()).getId())) {
					LOGGER.error("Current screen is not fully valid : {}", oldValue.intValue());
					tabPane.getSelectionModel().select(oldValue.intValue());
					return;
				}

				tabPane.getTabs().get(oldValue.intValue()).getStyleClass().remove(TAB_LABEL_ERROR_CLASS);

				// Safeguard against illegal index range issues
				int nextSelection = getNextSelection(tabPane, oldValue.intValue(), newSelection);
				if (nextSelection < oldValue.intValue()) {
					LOGGER.error("Next selection index is less than the old value");
					tabPane.getSelectionModel().select(oldValue.intValue());
					return;
				}

				tabPane.getSelectionModel().select(nextSelection);
			}
		});
	}


	private int getNextSelection(TabPane tabPane, int oldSelection, int newSelection) {
		if (newSelection - oldSelection <= 1) {
			return newSelection;
		}
		for (int i = oldSelection + 1; i < newSelection; i++) {
			if (!tabPane.getTabs().get(i).isDisabled()) {
				return oldSelection;
			}
		}
		return newSelection;
	}

	private boolean isScreenValid(final String screenName) {
		Optional<UiScreenDTO> result = orderedScreens.values().stream()
				.filter(screen -> screen.getName().equals(screenName.replace("_tab", EMPTY))).findFirst();

		boolean isValid = true;
		boolean isNotificationOfChangeFilled = false; // New flag for "Notification of Change" validation
		boolean isNotificationOfChangePresent = false; // Check if fields from this group exist on the screen

		if (result.isPresent()) {

			if (!isAdditionalInfoRequestIdProvided(result.get())) {
				showHideErrorNotification(ApplicationContext
						.getBundle(ApplicationContext.applicationLanguage(), RegistrationConstants.MESSAGES)
						.getString(RegistrationUIConstants.ADDITIONAL_INFO_REQ_ID_MISSING),null);
				return false;
			}

			for (UiFieldDTO field : result.get().getFields()) {

				// Check if the field belongs to the "Notification of Change" group
				if ("Notification of Change".equalsIgnoreCase(field.getAlignmentGroup())) {
					isNotificationOfChangePresent = true;        // Found relevant group fields on this screen
					FxControl control = getFxControl(field.getId());
					int fieldIdSize= field.getId().length();
					if (control != null && isFieldVisible(field) && !control.isEmpty() ){
						if( !field.getId().trim().startsWith("isError") && !field.getId().trim().startsWith("changeReason"))
							isNotificationOfChangeFilled = true;
					}
				}

				// Validate PRN differently
				if (field.getId().equalsIgnoreCase("PRN") && !isPrnValid) {
					LOGGER.error("PRN verification failed");
					String label = getFxControl(field.getId()).getUiSchemaDTO().getLabel()
							.getOrDefault(ApplicationContext.applicationLanguage(), field.getId());
					showHideErrorNotification(label,"");
					isValid = false;
					break;
				}


				if (getFxControl(field.getId()) != null && !getFxControl(field.getId()).canContinue()) {
					LOGGER.error("Screen validation , fieldId : {} has invalid value", field.getId());
					String label = getFxControl(field.getId()).getUiSchemaDTO().getLabel()
							.getOrDefault(ApplicationContext.applicationLanguage(), field.getId());
					showHideErrorNotification(label,field.getAlignmentGroup());
					isValid = false;
					break;
				}
			}
		}

		if (isValid) {
			showHideErrorNotification(null,null);
			auditFactory.audit(AuditEvent.REG_NAVIGATION, Components.REGISTRATION_CONTROLLER,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			// Only show the general notification if the screen is "Demographic Details"
			if (screenName != null && "DemographicDetails_tab".equalsIgnoreCase(screenName) && "NEW".equals(process.getId())) {
				showHideGeneralNotification("Please Note: Maiden name and any Previous names will not appear on the card");

			}

		}
		// Show error if fields from "Notification of Change" are present but none are filled
		if (isNotificationOfChangePresent && !isNotificationOfChangeFilled) {
			showHideErrorNotification("At least one field in the 'Notification of Change' section must be filled.",null);
			return false;
		}
		return isValid;
	}

	private boolean isFieldVisible(UiFieldDTO schemaDTO) {
		if (requiredFieldValidator == null) {
			requiredFieldValidator = ClientApplication.getApplicationContext().getBean(RequiredFieldValidator.class);
		}
		try {
			boolean isVisibleAccordingToSpec = requiredFieldValidator.isFieldVisible(schemaDTO, getRegistrationDTOFromSession());

			return isVisibleAccordingToSpec;
		} catch (Exception exception) {
			LOGGER.error("Failed to check field visibility", exception);
		}
		return true;
	}

	private void showHideGeneralNotification(String message) {
		Tooltip toolTip = new Tooltip(message);
		toolTip.prefWidthProperty().bind(notification.widthProperty());
		toolTip.setWrapText(true);
		notification.setTooltip(toolTip);
		notification.setText(message);
	}

	private void showHideErrorNotification(String fieldName, String groupName) {
		// Check if the fieldName is equal to the specified message
		if ("I have read and accept terms and conditions to share my Personal Identifying Information (PII)".equals(fieldName)) {
			// If true, set the custom message
			fieldName = "Please accept the terms and conditions to proceed with the application.";
		}

		Tooltip toolTip = new Tooltip(fieldName);
		toolTip.prefWidthProperty().bind(notification.widthProperty());
		toolTip.setWrapText(true);
		notification.setTooltip(toolTip);

		// Only show the custom message if the fieldName matches the condition
		if ("Please accept the terms and conditions to proceed with the application.".equals(fieldName)) {
			notification.setText(fieldName); // Show the message if condition is met
		} else {
			notification.setText(
					(fieldName == null) ? EMPTY
							: ApplicationContext
							.getBundle(ApplicationContext.applicationLanguage(),
									RegistrationConstants.MESSAGES)
							.getString("SCREEN_VALIDATION_ERROR")
							+ " [ " + fieldName + " ]"
							+ (groupName == null ? "" : " of " + groupName + " group")
			);
		}
	}


	private String getInvalidScreenName(TabPane tabPane) {
		String errorScreen = EMPTY;
		for (UiScreenDTO screen : orderedScreens.values()) {
			LOGGER.error("Started to validate screen : {} ", screen.getName());

			if (!isAdditionalInfoRequestIdProvided(screen)) {
				LOGGER.error("Screen validation failed {}, Additional Info request Id is required", screen.getName());
				errorScreen = screen.getName();
				break;
			}

			boolean anyInvalidField = screen.getFields().stream().anyMatch(
					field -> getFxControl(field.getId()) != null && getFxControl(field.getId()).canContinue() == false);

			Optional<Tab> result = tabPane.getTabs().stream()
					.filter(t -> t.getId().equalsIgnoreCase(screen.getName() + "_tab")).findFirst();
			if (anyInvalidField && result.isPresent()) {
				LOGGER.error("Screen validation failed {}", screen.getName());
				errorScreen = screen.getName();
				result.get().getStyleClass().add(TAB_LABEL_ERROR_CLASS);
				break;
			} else if (result.isPresent())
				result.get().getStyleClass().remove(TAB_LABEL_ERROR_CLASS);
		}
		return errorScreen;
	}

	private TabPane createTabPane(ProcessSpecDto processSpecDto) {
		TabPane tabPane = new TabPane();
		tabPane.setId(getRegistrationDTOFromSession().getRegistrationId());
		tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabPane.prefWidthProperty().bind(anchorPane.widthProperty());
		tabPane.prefHeightProperty().bind(anchorPane.heightProperty());

		setTabSelectionChangeEventHandler(tabPane);
		anchorPane.getChildren().add(tabPane);
		addNavigationButtons(processSpecDto);
		return tabPane;
	}

	public void populateScreens() throws Exception {
		RegistrationDTO registrationDTO = getRegistrationDTOFromSession();
		LOGGER.debug("Populating Dynamic screens for process : {}", registrationDTO.getProcessId());
		initialize(registrationDTO);
		ProcessSpecDto processSpecDto = getProcessSpec(registrationDTO.getProcessId(),
				registrationDTO.getIdSchemaVersion());
		getScreens(processSpecDto.getScreens());

		process = processSpecDto ;
		TabPane tabPane = createTabPane(processSpecDto);

		for (UiScreenDTO screenDTO : orderedScreens.values()) {
			Map<String, List<UiFieldDTO>> screenFieldGroups = getFieldsBasedOnAlignmentGroup(screenDTO.getFields());

			List<String> labels = new ArrayList<>();
			getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().forEach(langCode -> {
				labels.add(screenDTO.getLabel().get(langCode));
			});

			String tabNameInApplicationLanguage = screenDTO.getLabel()
					.get(getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0));

			if (screenFieldGroups == null || screenFieldGroups.isEmpty())
				continue;

			Tab screenTab = new Tab();
			screenTab.setId(screenDTO.getName() + "_tab");
			screenTab.setText(tabNameInApplicationLanguage == null ? labels.get(0) : tabNameInApplicationLanguage);
			screenTab.setTooltip(new Tooltip(String.join(RegistrationConstants.SLASH, labels)));

			GridPane screenGridPane = getScreenGridPane(screenDTO.getName());
			screenGridPane.prefWidthProperty().bind(tabPane.widthProperty());
			screenGridPane.prefHeightProperty().bind(tabPane.heightProperty());

			int rowIndex = 0;
			GridPane gridPane = getScreenGroupGridPane(screenGridPane.getId() + "_col_1", screenGridPane);

			if (screenDTO.isPreRegFetchRequired()) {
				gridPane.add(getPreRegistrationFetchComponent(processSpecDto.getFlow()), 0, rowIndex++);
			}
			if (screenDTO.isAdditionalInfoRequestIdRequired()) {
				additionalInfoReqIdScreenOrder = screenDTO.getOrder();
				gridPane.add(getAdditionalInfoRequestIdComponent(), 0, rowIndex++);
			}

			for (Entry<String, List<UiFieldDTO>> groupEntry : screenFieldGroups.entrySet()) {
				GridPane groupFlowPane = new GridPane();
				groupFlowPane.prefWidthProperty().bind(gridPane.widthProperty());
				groupFlowPane.setHgap(20);
				groupFlowPane.setVgap(20);

				if (screenDTO.getName().equals("DemographicDetails")) {
					groupFlowPane.getStyleClass().add("preRegParentPaneSection");
					groupFlowPane.setPadding(new Insets(20, 0, 20, 20));

					if (!(groupEntry.getKey().equals("Foundling Check") || groupEntry.getKey().equals("Declaration"))) {
						ColumnConstraints leftColumn = new ColumnConstraints();
						leftColumn.setPercentWidth(33);
						ColumnConstraints centerColumn = new ColumnConstraints();
						centerColumn.setPercentWidth(33);
						ColumnConstraints rightColumn = new ColumnConstraints();
						rightColumn.setPercentWidth(33);
						groupFlowPane.getColumnConstraints().addAll(leftColumn, centerColumn, rightColumn);
					}


					/* Adding Group label */
					Label label = new Label(groupEntry.getKey());
					label.getStyleClass().add("demoGraphicCustomLabel");
					label.setStyle("-fx-font-weight: 700; -fx-font-size: 15px;");

					if (groupEntry.getKey().equals("COP Categories and Services")) {
						label.setPadding(new Insets(0, 0, 10, 0));
					}

					groupFlowPane.add(label, 0, 0, 2, 1);
				}
				int fieldIndex = 0;
				int gRowIndex = 0;
				int gColIndex = 0;
				for (UiFieldDTO fieldDTO : groupEntry.getValue()) {
					try {
						if (fieldDTO.getParentFields() != null && !fieldDTO.getParentFields().isEmpty()) {
							fieldDTO.getParentFields().forEach(parent -> {
								FxControl control = getFxControl(parent);
								VBox vbox = (VBox) control.getNode();

								for (Node node : vbox.getChildren()) {
									if (node instanceof TitledPane) {
										TitledPane titledPane = (TitledPane) node;
										if (titledPane.getContent() instanceof GridPane) {
											GridPane sectionPane = (GridPane) titledPane.getContent();
											FxControl controlNode = null;
											try {
												controlNode = buildFxElement(fieldDTO);
											} catch (Exception e) {
												e.printStackTrace();
											}

											int[] lastPosition = getLastPosition(sectionPane);
											int lastRow = lastPosition[0];
											int lastColumn = lastPosition[1];

											int nextColumn;
											int nextRow;
											if (lastRow == -1 && lastColumn == -1) {
												nextRow = 0;
												nextColumn = 0;
											} else {
												nextColumn = (lastColumn + 1) % 3;
												nextRow = lastColumn == 2 ? lastRow + 1 : lastRow;
											}

											sectionPane.add(controlNode.getNode(), nextColumn, nextRow);
											titledPane.setContent(sectionPane);
										}
									}
								}
							});
							continue;
						}

						FxControl fxControl = buildFxElement(fieldDTO);
						if (fxControl.getNode() instanceof GridPane) {
							((GridPane) fxControl.getNode()).prefWidthProperty().bind(groupFlowPane.widthProperty());
						}
						// Check if the current field is pollingStationComment
						if ((groupEntry.getKey().equals("Voters Information") && fieldDTO.getId().equals("pollingStationComment"))) {
							// Dynamically adjust this specific field to span across the entire row (3 columns)
							GridPane.setColumnSpan(fxControl.getNode(), 3); // Span 3 columns for the row
							groupFlowPane.add(fxControl.getNode(), 0, (fieldIndex / 3) + 1, 3, 1); // Add it to the next available row with column span
						} else {
							if (screenDTO.getName().equals("DemographicDetails")) {
								fxControl.getNode().getStyleClass().add("demoGraphicCustomField");

								if (fieldDTO.getControlType().equals(CONTROLTYPE_TITLE)) {
									groupFlowPane.getColumnConstraints().clear();
									groupFlowPane.setVgap(0);
									groupFlowPane.add(fxControl.getNode(), 0, fieldIndex + 1);
								} else if (fieldDTO.getAlignmentGroup().equals("COP Categories and Services")) {
									if (fieldDTO.getControlType().equals(CONTROLTYPE_CHECKBOX)) {
										groupFlowPane.setVgap(0);
										gRowIndex++;
										gColIndex = 0;
										groupFlowPane.add(fxControl.getNode(), 0, gRowIndex);
										gRowIndex++;
									} else {
										groupFlowPane.setVgap(0);
										groupFlowPane.add(fxControl.getNode(), gColIndex, gRowIndex);
										gColIndex++;
										if (gColIndex >= 3) {
											gColIndex = 0;
											gRowIndex++;
										}
									}
								} else {
									groupFlowPane.add(fxControl.getNode(), (fieldIndex % 3), (fieldIndex / 3) + 1);
								}

								fieldIndex++;
							} else {
								if (screenDTO.getName().equals("Documents")) {
									fxControl.getNode().getStyleClass().add(RegistrationConstants.DOCUMENT_COMBOBOX_FIELD);
								}
								groupFlowPane.getChildren().add(fxControl.getNode());
							}
						}

						// Only if field is PRN
						if (fieldDTO.getId().equalsIgnoreCase("PRNId")) {
							Node node = fxControl.getNode();

							Button validatePaymentButton = new Button("Validate Payment");
							validatePaymentButton.setPrefWidth(200);
							validatePaymentButton
									.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white;");

							VBox paymentVBox = new VBox(10);

							paymentVBox.getChildren().addAll(validatePaymentButton);
							groupFlowPane.add(paymentVBox, (fieldIndex % 3), (fieldIndex / 3) + 1);
							fieldIndex++;

							validatePaymentButton.setOnAction(event -> {
								handlePRNVerification(fxControl.getData().toString(), node,
										processSpecDto.getFlow(), registrationDTO.getRegistrationId(),
										fxControl, groupFlowPane);

							});
						}

					} catch (Exception exception) {
						LOGGER.error("Failed to build control " + fieldDTO.getId(), exception);
					}
				}

				// Hide introducer grouping for adults
				if (groupEntry.getKey().equals("Introducer")) {
					groupFlowPane.visibleProperty()
							.bind(Bindings.or(groupFlowPane.getChildren().get(1).visibleProperty(),
									groupFlowPane.getChildren().get(2).visibleProperty()));
				}
				gridPane.add(groupFlowPane, 0, rowIndex++);
			}

			screenGridPane.setStyle("-fx-background-color: white;");
			screenGridPane.add(gridPane, 1, 1);
			final ScrollPane scrollPane = new ScrollPane(screenGridPane);
			scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			scrollPane.setId("scrollPane");
			screenTab.setContent(scrollPane);
			tabPane.getTabs().add(screenTab);
		}

// refresh to reflect the initial visibility configuration
		refreshFields();
		addPreviewAndAuthScreen(tabPane);

	}

	private int[] getLastPosition(GridPane gridPane) {
		int lastRow = -1;
		int lastColumn = -1;

		for (Node node : gridPane.getChildren()) {
			Integer rowIndex = GridPane.getRowIndex(node);
			Integer columnIndex = GridPane.getColumnIndex(node);

			if (rowIndex == null) rowIndex = 0;
			if (columnIndex == null) columnIndex = 0;

			if (rowIndex > lastRow) {
				lastRow = rowIndex;
				lastColumn = columnIndex;
			} else if (rowIndex == lastRow && columnIndex > lastColumn) {
				lastColumn = columnIndex;
			}
		}

		return new int[]{lastRow, lastColumn};
	}

	/**
	 * This method helps in verifying Payment Registration Numbers (PRNs) with the NIRA Payment Gateway service
	 * and returns true or false if valid (status is paid and not consumed before)
	 *
	 * @param prnText
	 * @param processFlow
	 * @param regId
	 * @return
	 */
	public PRNVerificationResponse verifyPRN(final String prnText, final String processFlow, final String regId) {

	 	CheckPRNStatusResponseDTO responseDTO = prnService.checkPRNStatus(prnText);

		if (responseDTO != null) {
			if (responseDTO.getStatusCode().equalsIgnoreCase(statusCode)) {
				if (responseDTO.getEligiblePaidForServiceTypes().get("eligiblePaidForServiceTypes") != null &&
						responseDTO.getEligiblePaidForServiceTypes().get("eligiblePaidForServiceTypes").equalsIgnoreCase(processFlow)) {
					Boolean prnCheck = checkPrnInTranscLogs(prnText, regId).isValid();
					if (prnCheck == null) {
						return new PRNVerificationResponse(false, "Verification failed.");
					}

					if (!prnCheck) {
						return new PRNVerificationResponse(true, "PRN validation is Success. Continue with Application");
					}
				} else {
					return new PRNVerificationResponse(false, String.format("Verification failed: PRN isn't for %s usecase", processFlow));
				}
			} else {
				return new PRNVerificationResponse(false, String.format("Verification failed: PRN isn't paid"));
			}
		}
		return new PRNVerificationResponse(false, "Invalid PRN. Please make the payment to continue with Application.");
	}


	/**
	 * This method checks whether the PRN was consumed before / used before
	 *
	 *
	 * @param prnText
	 * @param regId
	 * @return
	 */
	private PRNVerificationResponse checkPrnInTranscLogs(final String prnText, final String regId) {
		CheckPRNInTransLogsResponseDTO logsResponse = null;

		try {
			logsResponse = prnService.checkPrnInTransLogs(prnText);
		} catch (Exception e) {
			LOGGER.error("Transaction logs service unreachable: " + e.getMessage());
			return new PRNVerificationResponse(false, "Failed to reach PRN service");
		}

		if (logsResponse != null) {
			if (logsResponse.isPresentInLogs() && logsResponse.getRegIdTagged() != null) {
				if (regId.equals(logsResponse.getRegIdTagged())) {
					LOGGER.info("PRN is present in logs but matches the current session regId.");
					return new PRNVerificationResponse(false, "PRN matches the current regId.");
				} else {
					LOGGER.info(String.format("PRN is already consumed and tagged to a different regId: %s", logsResponse.getRegIdTagged()));
					return new PRNVerificationResponse(false, "PRN already used");
				}
			}
		}

		return new PRNVerificationResponse(false, "PRN is not consumed.");
	}


	/**
	 * This method consumes a PRN as used
	 *
	 * @param prnText
	 * @param regId
	 * @return
	 */
	private PRNVerificationResponse consumePrnAsUsed(final String prnText, final String regId) {
		ConsumePRNResponseDTO consumeResponse = null;

		try {
			consumeResponse = prnService.consumePrn(prnText, regId);
		} catch (Exception e) {
			LOGGER.error("Failed to reach consume PRN service: " + e.getMessage());
			return new PRNVerificationResponse(false, "Failed to reach PRN service");
		}

		if (consumeResponse != null) {
			if (!consumeResponse.isConsumedSucess()) {
				if (consumeResponse.getRegIdTaggedToPrn() != null) {
					if (regId.equals(consumeResponse.getRegIdTaggedToPrn())) {
						LOGGER.info("PRN is already tagged to the current session regId. PRN consumption successful");
						return new PRNVerificationResponse(true, "PRN validation is Success. Continue with Application");
					} else {
						LOGGER.info(String.format("PRN is tagged to a different regId: %s", consumeResponse.getRegIdTaggedToPrn()));
						return new PRNVerificationResponse(false, "PRN already used");
					}
				} else {
					LOGGER.info("PRN consumption failed: No regId tagged to this PRN.");
					return new PRNVerificationResponse(false, "PRN consumption failed");
				}
			}
			LOGGER.info("PRN consumption successful");
			return new PRNVerificationResponse(true, "PRN validation is Success. Continue with Application");
		}
		LOGGER.error("PRN consumption failed: No response from service.");
		return new PRNVerificationResponse(false, "PRN consumption failed");
	}


	private void updatePRNValidationMessage(Label validationLabel, String message, boolean isSuccess) {
		if (validationLabel != null) {
			validationLabel.setText(message);

			// Apply custom styles based on success or failure
			if (isSuccess) {
				validationLabel.getStyleClass().removeAll("error-prn-label");
				validationLabel.getStyleClass().add("success-prn-label");
			} else {
				validationLabel.getStyleClass().removeAll("success-prn-label");
				validationLabel.getStyleClass().add("error-prn-label");
			}

			validationLabel.setVisible(true);
		}
	}

	/**
	 * This method handles the PRN verification overall and displays corresponding messages if PRN is valid or not
	 *
	 * @param prnText
	 * @param node
	 * @param processSpecFlow
	 * @param registrationId
	 * @param fxControl
	 * @param parentGridPane
	 */
	private void handlePRNVerification(String prnText, Node node, String processSpecFlow, String registrationId, FxControl fxControl, GridPane parentGridPane) {
		showLoadingPRNIndicator(node);

		new Thread(() -> {
			try {
				Thread.sleep(3000);

				PRNVerificationResponse verificationResponse = verifyPRN(prnText, processSpecFlow, registrationId);
				isPrnValid = verificationResponse.isValid();

				Platform.runLater(() -> {
					removeLoadingPRNIndicator(node);

					// Locate the existing PRN validation label inside the GridPane
					Label validationLabel = (Label) parentGridPane.lookup("#PRNengMessage");

					if (isPrnValid) {
						PRNVerificationResponse consumeResponse = consumePrnAsUsed(prnText, registrationId);
						showPaymentValidationPopup(parentGridPane, consumeResponse.getMessage(), true);
						//updatePRNIndicator(node, consumeResponse.isValid());
						//updatePRNValidationMessage(validationLabel, consumeResponse.getMessage(), consumeResponse.isValid());
					} else {
						showPaymentValidationPopup(parentGridPane, verificationResponse.getMessage(), false);
						//updatePRNIndicator(node, false);
						//updatePRNValidationMessage(validationLabel, verificationResponse.getMessage(), false);
					}
				});
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}).start();
	}

	private void showPaymentValidationPopup(GridPane parent, String message, boolean isSuccess) {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(parent.getScene().getWindow());
		dialog.setHeaderText("Payment Validation");

		// Styling the header text
		Label headerLabel = (Label) dialog.getDialogPane().lookup(".header-panel .label");
		if (headerLabel != null) {
			headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;"); // Set font size and make bold
		}

		dialog.setResizable(false);

		// Prevent dialog from closing via close button
		Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
		dialogStage.setOnCloseRequest(event -> {
			event.consume(); // Prevents the dialog from closing via the close button
		});

		// Create content label
		Label contentLabel = new Label(message);
		contentLabel.setStyle("-fx-font-size: 14px;-fx-text-fill: black;");
		contentLabel.setWrapText(true);

		// Set content layout
		VBox dialogContent = new VBox(10, contentLabel);
		dialogContent.setAlignment(Pos.CENTER);
		dialog.getDialogPane().setContent(dialogContent);

		// Set styles based on success or failure
		if (isSuccess) {
			dialog.getDialogPane().lookup(".header-panel")
					.setStyle("-fx-background-color: #4CAF50; -fx-max-height: 20px;");
			dialog.getDialogPane().lookup(".header-panel .label")
					.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
			dialogContent.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 20px; -fx-max-height: 50px");
		} else {
			dialog.getDialogPane().lookup(".header-panel")
					.setStyle("-fx-background-color: #d32f2f; -fx-max-height: 20px; -fx-min-height:20px;");
			dialog.getDialogPane().lookup(".header-panel .label")
					.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
			dialogContent.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 20px; -fx-max-height: 50px");
		}

		// Set dialog width
		dialog.getDialogPane().setMinWidth(450);
		dialog.getDialogPane().setMaxWidth(450);

		// Add OK button
		dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);

		// Get the OK button and set its action
		Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.setOnAction(event -> {
			// Method to call when OK button is clicked
			if (!isSuccess) {
				try {
					BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
					clearOnboardData();
					clearRegistrationData();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		// Show the dialog
		dialog.showAndWait();
	}


	private void showLoadingPRNIndicator(Node node) {
		node.getStyleClass().removeAll("success-indicator", "error-indicator");
		node.getStyleClass().add("loading-indicator");
	}

	private void removeLoadingPRNIndicator(Node node) {
		node.getStyleClass().remove("loading-indicator");
	}

	private void updatePRNIndicator(Node node, boolean isVerified) {
		node.getStyleClass().removeAll("loading-indicator", "success-indicator", "error-indicator");

		if (isVerified) {
			node.getStyleClass().add("success-indicator");
		} else {
			node.getStyleClass().add("error-indicator");
		}
	}

	private void addPreviewAndAuthScreen(TabPane tabPane) throws Exception {
		List<String> previewLabels = new ArrayList<>();
		List<String> authLabels = new ArrayList<>();
		for (String langCode : getRegistrationDTOFromSession().getSelectedLanguagesByApplicant()) {
			previewLabels.add(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
					.getString(RegistrationConstants.previewHeader));
			authLabels.add(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
					.getString(RegistrationConstants.authentication));
		}

		String langCode = getRegistrationDTOFromSession().getSelectedLanguagesByApplicant().get(0);

		Tab previewScreen = new Tab();
		previewScreen.setId("PREVIEW");
		previewScreen.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString(RegistrationConstants.previewHeader));
		previewScreen.setTooltip(new Tooltip(String.join(RegistrationConstants.SLASH, previewLabels)));
		tabPane.getTabs().add(previewScreen);

		Tab authScreen = new Tab();
		authScreen.setId("AUTH");
		authScreen.setText(ApplicationContext.getBundle(langCode, RegistrationConstants.LABELS)
				.getString(RegistrationConstants.authentication));
		authScreen.setTooltip(new Tooltip(String.join(RegistrationConstants.SLASH, authLabels)));
		tabPane.getTabs().add(authScreen);
	}

	private void loadPreviewOrAuthScreen(TabPane tabPane, Tab tab) {
		switch (tab.getId()) {
			case "PREVIEW":
				try {
					tabPane.getSelectionModel().select(tab);
					tab.setContent(getPreviewContent(tabPane));
				} catch (Exception exception) {
					LOGGER.error("Failed to load preview page!!, clearing registration data.");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants
							.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_PREVIEW_PAGE));
				}
				break;

			case "AUTH":
				try {
					tabPane.getSelectionModel().select(tab);
					tab.setContent(loadAuthenticationPage(tabPane));
					authenticationController.initData(ProcessNames.PACKET.getType());
				} catch (Exception exception) {
					LOGGER.error("Failed to load auth page!!, clearing registration data.");
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants
							.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_APPROVAL_PAGE));
				}
				break;
		}
	}

	private Node getPreviewContent(TabPane tabPane) throws Exception {
		String content = registrationPreviewController.getPreviewContent();
		if (content != null) {
			final WebView webView = new WebView();
			webView.setId("webView");
			webView.prefWidthProperty().bind(tabPane.widthProperty());
			webView.prefHeightProperty().bind(tabPane.heightProperty());
			webView.getEngine().loadContent(content);
			final GridPane gridPane = new GridPane();
			gridPane.prefWidthProperty().bind(tabPane.widthProperty());
			gridPane.prefHeightProperty().bind(tabPane.heightProperty());
			gridPane.setAlignment(Pos.TOP_LEFT);
			gridPane.getChildren().add(webView);
			return gridPane;
		}
		throw new RegBaseCheckedException("", "Failed to load preview screen");
	}

	private Node loadAuthenticationPage(TabPane tabPane) throws Exception {
		GridPane gridPane = (GridPane) BaseController.load(getClass().getResource(REG_AUTH_PAGE));
		gridPane.prefWidthProperty().bind(tabPane.widthProperty());
		gridPane.prefHeightProperty().bind(tabPane.heightProperty());

		Node node = gridPane.lookup("#backButton");
		if (node != null) {
			node.setVisible(false);
			node.setDisable(true);
		}

		node = gridPane.lookup("#operatorAuthContinue");
		if (node != null) {
			node.setVisible(false);
			node.setDisable(true);
		}
		return gridPane;
	}

	private FxControl buildFxElement(UiFieldDTO uiFieldDTO) throws Exception {
		LOGGER.info("Building fxControl for field : {}", uiFieldDTO.getId());
		FxControl fxControl = null;

		if (uiFieldDTO.getControlType() != null) {
			switch (uiFieldDTO.getControlType()) {
				case CONTROLTYPE_TEXTFIELD:
					fxControl = new TextFieldFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_BIOMETRICS:
					fxControl = new BiometricFxControl(/* getProofOfExceptionFields() */).build(uiFieldDTO);
					break;

				case CONTROLTYPE_BUTTON:
					fxControl = new ButtonFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_CHECKBOX:
					fxControl = new CheckBoxFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOB:
					fxControl = new DOBFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOB_AGE:
					fxControl = new DOBAgeFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DOCUMENTS:
					fxControl = new DocumentFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_DROPDOWN:
					fxControl = new DropDownFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_HTML:
					fxControl = new HtmlFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_COMMENT:
					fxControl = new CommentFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_TITLE:
					fxControl = new TitleFxControl().build(uiFieldDTO);
					break;

				case CONTROLTYPE_TOGGLE_BUTTON:
					fxControl = new ToggleButtonFxControl().build(uiFieldDTO);
					break;
			}
		}

		if (fxControl == null)
			throw new Exception("Failed to build fxControl");

		fxControlMap.put(uiFieldDTO.getId(), fxControl);
		return fxControl;
	}

	public void refreshFields() {
		orderedScreens.values().forEach(screen -> {
			refreshScreenVisibility(screen.getName());
		});
	}

	public void refreshDependentFields(List<String> dependentFields) {
		orderedScreens.values().forEach(screen -> { refreshScreenVisibilityForDependentFields(screen.getName(), dependentFields); });
	}

	public void resetValue() {
		if(preregFetching == false) {
			for (UiScreenDTO screenDTO : orderedScreens.values()) {
				for (UiFieldDTO field : screenDTO.getFields()) {
					FxControl fxControl = getFxControl(field.getId());
					if (fxControl != null) {
						switch (fxControl.getUiSchemaDTO().getType()) {
							case "biometricsType":
								fxControl.selectAndSet(null);
								break;
							case "documentType":
								fxControl.clearValue();
								break;
							default:
								if(!field.isSetRequired() && screenDTO.getOrder() == 2 && process!=null && !("UPDATE".equals(process.getId()))){
									fxControl.selectAndSet(null);
									fxControl.setData(null);
									fxControl.clearToolTipText();
								}
								if (field.getDefaultValue() != null) {
									boolean check = fxControl.isFieldDefaultValue(field);
									if (check) {
										fxControl.selectAndSet("Y");
										fxControl.getNode().setDisable(true);
									} else {
										fxControl.selectAndSet(null);
										fxControl.getNode().setDisable(false);
									}
								}
						}
					}
				}
			}
		}
		else{
			for (UiScreenDTO screenDTO : orderedScreens.values()) {
				for (UiFieldDTO field : screenDTO.getFields()) {
					FxControl fxControl = getFxControl(field.getId());
					if (fxControl != null) {
						if (field.getDefaultValue() != null) {
							boolean check = fxControl.isFieldDefaultValue(field);
							if (check) {
								fxControl.selectAndSet("Y");
								fxControl.getNode().setDisable(true);
							} else {
								fxControl.selectAndSet(null);
								fxControl.getNode().setDisable(false);
							}
						}
					}
				}
			}
		}
	}

	public void checkResetValueforDOBAgeControl(){
		if (process!=null && process.getId().equalsIgnoreCase("UPDATE")){
			resetValue();
		}

	}


	public String processCheck(){
		return process.getId() ;
	}

	public HashMap<String, Object> ageRestriction(int age, int highAgeNew, int highAgeFirstId){

		HashMap<String, Object> result = new HashMap<>();

		RegistrationDTO registrationDTO = getRegistrationDTOFromSession();

		if ("NEW".equals(registrationDTO.getProcessId())) {
			List<SimpleDto> userService = (List<SimpleDto>) registrationDTO.getDemographicSimpleType("userServiceType");
			if (age < highAgeNew && ("By Registration".equals(userService.get(0).getValue()) || "By Naturalization".equals(userService.get(0).getValue()))) {
				result.put("isValid", false);
				result.put("errVal", highAgeNew);
				return result;
			}

		}
		else if ("FIRSTID".equals(registrationDTO.getProcessId()) && age < highAgeFirstId) {
			result.put("isValid", false);
			result.put("errVal", highAgeFirstId);
			return result;
		};

		result.put("isValid", true);
		result.put("errVal", "");
		return result;
	}

	/*
	 * public List<UiFieldDTO> getProofOfExceptionFields() { return
	 * fields.stream().filter(field ->
	 * field.getSubType().contains(RegistrationConstants.POE_DOCUMENT)).collect(
	 * Collectors.toList()); }
	 */

	private FxControl getFxControl(String fieldId) {
		return GenericController.getFxControlMap().get(fieldId);
	}

	public Stage getKeyboardStage() {
		return keyboardStage;
	}

	public void setKeyboardStage(Stage keyboardStage) {
		this.keyboardStage = keyboardStage;
	}

	public boolean isKeyboardVisible() {
		return keyboardVisible;
	}

	public void setKeyboardVisible(boolean keyboardVisible) {
		this.keyboardVisible = keyboardVisible;
	}

	public String getPreviousId() {
		return previousId;
	}

	public void setPreviousId(String previousId) {
		this.previousId = previousId;
	}

	public String getCurrentScreenName() {
		TabPane tabPane = (TabPane) anchorPane.lookup(HASH + getRegistrationDTOFromSession().getRegistrationId());
		return tabPane.getSelectionModel().getSelectedItem().getId().replace("_tab", EMPTY);
	}

}