package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.RestartController;
import io.mosip.registration.controller.SettingsController;
import io.mosip.registration.controller.auth.LoginController;
import io.mosip.registration.controller.device.Streamer;
import io.mosip.registration.dto.ErrorResponseDTO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.SuccessResponseDTO;
import io.mosip.registration.dto.UserDTO;
import io.mosip.registration.dto.schema.SettingsSchema;
import io.mosip.registration.exception.PreConditionCheckException;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.jobs.BaseJob;
import io.mosip.registration.scheduler.SchedulerUtil;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.IdentitySchemaService;
import io.mosip.registration.service.config.JobConfigurationService;
import io.mosip.registration.service.config.LocalConfigService;
import io.mosip.registration.service.login.LoginService;
import io.mosip.registration.service.operator.UserDetailService;
import io.mosip.registration.service.remap.CenterMachineReMapService;
import io.mosip.registration.service.sync.MasterSyncService;
import io.mosip.registration.service.sync.SyncStatusValidatorService;
import io.mosip.registration.update.SoftwareUpdateHandler;
import io.mosip.registration.util.healthcheck.RegistrationSystemPropertiesChecker;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

/**
 * Class for Registration Officer details
 *
 * @author Sravya Surampalli
 * @author Balaji Sridharan
 * @since 1.0.0
 *
 */
@Controller
public class HeaderController extends BaseController {

	/**
	 * o Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(HeaderController.class);

	@FXML
	private Label registrationOfficerName;

	@FXML
	private Label registrationOfficeId;

	@FXML
	private Label registrationOfficeLocation;

	@FXML
	private MenuBar menu;

	@FXML
	private ImageView availableIcon;
	@FXML
	private ImageView mosipLogo;
	@FXML
	private ImageView userImageView;
	@FXML
	private ImageView regCenterLocationImgView;
	@FXML
	private ImageView registrationOfficeIdImageView;
	@FXML
	private ImageView availableIcon1;
	@FXML
	private ImageView homeSelectionMenuImageView;
	@FXML
	private ImageView homeImgView;
	

	@FXML
	private HBox online;

	@FXML
	private HBox offline;

	@FXML
    private HBox settingsHBox;

	@FXML
	private Menu homeSelectionMenu;

	@FXML
	private MenuItem userGuide;

	@FXML
	private MenuItem resetPword;

	@FXML
	private HBox settingsIconHBox;

	@Autowired
	private JobConfigurationService jobConfigurationService;

	@Autowired
	private MasterSyncService masterSyncService;

	@Autowired
	PacketHandlerController packetHandlerController;

	@Autowired
	private RestartController restartController;

	@Autowired
	private SoftwareUpdateHandler softwareUpdateHandler;

	@Autowired
	private HomeController homeController;

	ProgressIndicator progressIndicator;

	@Autowired
	private SyncStatusValidatorService statusValidatorService;

	@Autowired
	private LoginController loginController;

	@Autowired
	private Streamer streamer;

	@Autowired
	private CenterMachineReMapService centerMachineReMapService;

	@Autowired
	private LoginService loginService;

	@Autowired
	private UserDetailService userDetailService;

	@Autowired
	private BaseService baseService;

	@Autowired
    private IdentitySchemaService identitySchemaService;

    @Autowired
	private SettingsController settingsController;
    
    @Autowired
	private LocalConfigService localConfigService;

    private List<SettingsSchema> settingsByRole = new ArrayList<>();
	

	/**
	 * Mapping Registration Officer details
	 */
	public void initialize() {

		LOGGER.info(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
				"Displaying Registration Officer details");

		
		setImage(mosipLogo	, RegistrationConstants.MOSIP_LOGO_SMALL_IMG);
		setImage(userImageView	, RegistrationConstants.USER_IMG);
		setImage(regCenterLocationImgView	, RegistrationConstants.REG_CENTER_LOCATION_IMG);
		setImage(registrationOfficeIdImageView	, RegistrationConstants.SYSTEM_IMG);
		setImage(availableIcon1	, "Online.png");
		setImage(availableIcon	, "Offline.png");
		setImage(homeSelectionMenuImageView	, RegistrationConstants.HAMBURGER_IMG);
		setImage(homeImgView	, RegistrationConstants.HOME_IMG);

		registrationOfficerName.setText(SessionContext.userContext().getName());
		registrationOfficeId.setText(RegistrationSystemPropertiesChecker.getMachineId());
		registrationOfficeLocation
				.setText(SessionContext.userContext().getRegistrationCenterDetailDTO().getRegistrationCenterName() + " ("
						+ SessionContext.userContext().getRegistrationCenterDetailDTO().getRegistrationCenterId()
						+ ")");
		menu.setBackground(Background.EMPTY);

		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)
				&& !(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER_UPDATE)) {
			homeSelectionMenu.getItems().remove(0, homeSelectionMenu.getItems().size() - 3);
		} else {
			homeSelectionMenu.setDisable(false);
		}
		resetPword.setVisible(ApplicationContext.map().containsKey(RegistrationConstants.RESET_PWORD_URL));

		try {
			settingsByRole.clear();
			List<SettingsSchema> settingsSchema = identitySchemaService
					.getSettingsSchema(identitySchemaService.getLatestEffectiveSchemaVersion());
			settingsSchema = Arrays.asList(
					new SettingsSchema(
							"scheduledjobs",
							new HashMap<>(Map.of("eng", "Scheduled Jobs Settings")),
							new HashMap<>(Map.of("eng", "Scheduled Jobs Settings")),
							"ScheduledJobsSettings.fxml",
							"scheduledjobs.png",
							"scheduledjobs-shortcut.png",
							"1",
							Arrays.asList("REGISTRATION_SUPERVISOR")
					),
					new SettingsSchema(
							"globalconfigs",
							new HashMap<>(Map.of("eng", "Global Config Settings")),
							new HashMap<>(Map.of("eng", "Global Config Settings")),
							"GlobalConfigSettings.fxml",
							"globalconfigs.png",
							"globalconfigs-shortcut.png",
							"2",
							Arrays.asList("REGISTRATION_SUPERVISOR", "REGISTRATION_OFFICER")
					),
					new SettingsSchema(
							"devices",
							new HashMap<>(Map.of("eng", "Device Settings")),
							new HashMap<>(Map.of("eng", "Device Settings")),
							"DeviceSettings.fxml",
							"devices.png",
							"devices-shortcut.png",
							"3",
							Arrays.asList("REGISTRATION_SUPERVISOR", "REGISTRATION_OFFICER")
					)
			);
			if (settingsSchema != null && !settingsSchema.isEmpty()) {
				List<String> userRoles = userDetailService.getUserRoleByUserId(SessionContext.userId());
				settingsByRole = settingsSchema.stream()
						.filter(settings -> CollectionUtils.containsAny(settings.getAccessControl(), userRoles))
						.collect(Collectors.toList());
				if (settingsByRole != null && !settingsByRole.isEmpty()) {
					settingsIconHBox.setVisible(true);
					Optional<SettingsSchema> deviceSettings = settingsByRole.stream().filter(
							settings -> settings.getName().equalsIgnoreCase(RegistrationConstants.DEVICE_SETTINGS_NAME))
							.findAny();
					if (deviceSettings.isPresent()
							&& localConfigService.getValue(RegistrationConstants.DEVICES_SHORTCUT_PREFERENCE_NAME)
									.equalsIgnoreCase(RegistrationConstants.ENABLE)) {
						settingsController.createShortCut(deviceSettings.get());
					}
				}
			}
		} catch (RuntimeException | RegBaseCheckedException exception) {
			LOGGER.error("Exception while reading settings", exception);
		}

		getTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				Boolean flag = serviceDelegateUtil.isNetworkAvailable();
				online.setVisible(flag);
				offline.setVisible(!flag);
			}
		}, 0, (long)15 * 60 * 1000);
	}
	
	public void logout() {
		streamer.stop();
		auditFactory.audit(AuditEvent.LOGOUT_USER, Components.NAVIGATION, SessionContext.userContext().getUserId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		LOGGER.info(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
				"Clearing Session context" + SessionContext.authTokenDTO());

		closeAlreadyExistedAlert();

		logoutCleanUp();
	}

	/**
	 * Redirecting to Home page on Logout and destroying Session context
	 *
	 * @param event logout event
	 */
	public void logout(ActionEvent event) {
		streamer.stop();
		if (pageNavigantionAlert()) {
			auditFactory.audit(AuditEvent.LOGOUT_USER, Components.NAVIGATION, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			LOGGER.info(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
					"Clearing Session context");

			closeAlreadyExistedAlert();

			logoutCleanUp();
		}
	}

	/**
	 * Logout clean up.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void logoutCleanUp() {

		try {
			ApplicationContext.map().remove(RegistrationConstants.USER_DTO);

			SessionContext.destroySession();
			SchedulerUtil.stopScheduler();
			stopTimer();
			BorderPane loginpage = BaseController.load(getClass().getResource(RegistrationConstants.INITIAL_PAGE));

			getScene(loginpage);
		} catch (IOException ioException) {
			LOGGER.error(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_LOGOUT_PAGE));
		}
	}

	/**
	 * Redirecting to Home page
	 *
	 * @param event event for redirecting to home
	 */
	@FXML
	public void redirectHome(MouseEvent event) {

		Object obj = SessionContext.map().get(RegistrationConstants.ONBOARD_USER);
		if (obj != null && (boolean) obj) {
			goToHomePageFromOnboard();
		} else {
			goToHomePageFromRegistration();
		}

		// Enable Auto-Logout
		SessionContext.setAutoLogout(true);

	}

	/**
	 * Sync data through batch jobs.
	 *
	 * @param event the event
	 */
	public void syncData(ActionEvent event) {

		try {
			redirectHome(null);
			// Clear all registration data
			clearRegistrationData();

			if (!proceedOnAction("MS"))
				return;

			try {
				auditFactory.audit(AuditEvent.NAV_SYNC_DATA, Components.NAVIGATION,
						SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());
				executeSyncDataTask();
			} catch (RuntimeException runtimeException) {
				LOGGER.error(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
						runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			}

		} catch (RuntimeException exception) {
			LOGGER.error("REGISTRATION - REDIRECTHOME - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		}

	}

	/**
	 * Redirecting to PacketStatusSync Page
	 *
	 * @param event event for sync packet status
	 */
	public void syncPacketStatus(ActionEvent event) {
		if (!proceedOnAction("PS"))
			return;

		try {
			auditFactory.audit(AuditEvent.SYNC_REGISTRATION_PACKET_STATUS, Components.SYNC_SERVER_TO_CLIENT,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			AnchorPane syncServerClientRoot = BaseController
					.load(getClass().getResource(RegistrationConstants.SYNC_STATUS));

			if (!validateScreenAuthorization(syncServerClientRoot.getId())) {
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.AUTHORIZATION_ERROR));
			} else {
				VBox pane = (VBox) (menu.getParent().getParent().getParent());
				for (int index = pane.getChildren().size() - 1; index > 0; index--) {
					pane.getChildren().remove(index);
				}
				pane.getChildren().add(syncServerClientRoot);

				// Clear all registration data
				clearRegistrationData();

				// Enable Auto-Logout
				SessionContext.setAutoLogout(true);

			}
		} catch (IOException ioException) {
			LOGGER.error(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID, ioException.getMessage());
		}
	}

	/**
	 * This method is to trigger the Pre registration sync service
	 *
	 * @param event event for downloading pre reg data
	 */
	@FXML
	public void downloadPreRegData(ActionEvent event) {

		try {
			// Go To Home Page
			redirectHome(null);

			// Clear all registration data
			clearRegistrationData();

			if (!proceedOnAction(RegistrationConstants.OPT_TO_REG_PDS_J00003))
				return;

			auditFactory.audit(AuditEvent.SYNC_PRE_REGISTRATION_PACKET, Components.SYNC_SERVER_TO_CLIENT,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			executeDownloadPreRegDataTask(packetHandlerController.getPreRegDataPane());

		} catch (RuntimeException exception) {
			LOGGER.error("REGISTRATION - REDIRECTHOME - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE));
		}

	}

	public void uploadPacketToServer() {
		if (pageNavigantionAlert()) {

			if (!proceedOnAction("PS"))
				return;

			auditFactory.audit(AuditEvent.SYNC_PRE_REGISTRATION_PACKET, Components.SYNC_SERVER_TO_CLIENT,
					SessionContext.userContext().getUserId(), AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			packetHandlerController.uploadPacket();
		}
	}

	public void intiateRemapProcess() {
		if (pageNavigantionAlert()) {
			progressIndicator = packetHandlerController.getProgressIndicator();
			Service<Boolean> remapTaskService = new Service<Boolean>() {
				@Override
				protected Task<Boolean> createTask() {
					return /**
							 * @author SaravanaKumar
							 *
							 */
					new Task<Boolean>() {
						/*
						 * (non-Javadoc)
						 * 
						 * @see javafx.concurrent.Task#call()
						 */
						@Override
						protected Boolean call() throws RegBaseCheckedException {
							LOGGER.info("REGISTRATION - SYNC - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
									"Executing client settings to check remap process");
							masterSyncService.getMasterSync(RegistrationConstants.OPT_TO_REG_MDS_J00001,
									RegistrationConstants.JOB_TRIGGER_POINT_USER);
							return true;
						}
					};
				}
			};
			progressIndicator.progressProperty().bind(remapTaskService.progressProperty());
			remapTaskService.setOnFailed((new EventHandler<WorkerStateEvent>() {
				@Override
				public void handle(WorkerStateEvent t) {
					LOGGER.debug("REGISTRATION - REDIRECTHOME - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
							"check for Center remap process failed");
					generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SYNC_FAILURE));
					machineRemapCheck(true);
					progressIndicator.setVisible(false);
				}
			}));

			remapTaskService.setOnSucceeded(((new EventHandler<WorkerStateEvent>() {
				@Override
				public void handle(WorkerStateEvent t) {
					LOGGER.debug("REGISTRATION - REDIRECTHOME - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
							"check for Center remap process success");
					machineRemapCheck(true);
					progressIndicator.setVisible(false);
				}

			})));

			if (!proceedOnAction("RM"))
				return;

			progressIndicator = packetHandlerController.getProgressIndicator();
			GridPane gridPane = homeController.getMainBox();
			gridPane.setDisable(true);
			progressIndicator.setVisible(true);
			remapTaskService.start();
		}
	}

	@FXML
	public void hasUpdate(ActionEvent event) {
		if (pageNavigantionAlert()) {

			try {
				baseService.proceedWithSoftwareUpdate();
			} catch (PreConditionCheckException e) {
				generateAlert(RegistrationConstants.ERROR, ApplicationContext.getInstance()
						.getApplicationLanguageMessagesBundle().getString(e.getErrorCode()));
				return;
			}

			boolean hasUpdate = hasUpdate();
			if (hasUpdate) {
				softwareUpdate(homeController.getMainBox(), packetHandlerController.getProgressIndicator(),
						RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UPDATE_LATER), true);
			} else {
				generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_UPDATES_FOUND));
			}
		}
	}

	public boolean hasUpdate() {
		boolean hasUpdate = softwareUpdateHandler.hasUpdate();
		Timestamp timestamp = hasUpdate ? softwareUpdateHandler.getLatestVersionReleaseTimestamp()
				: Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());
		globalParamService.updateSoftwareUpdateStatus(hasUpdate, timestamp);
		return hasUpdate;
	}

	private String softwareUpdate() {
		try {

			softwareUpdateHandler.doSoftwareUpgrade();
			return RegistrationConstants.ALERT_INFORMATION;

		} catch (Exception exception) {
			LOGGER.error(LoggerConstants.LOG_REG_HEADER, APPLICATION_NAME, APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			return RegistrationConstants.ERROR;

		}
	}

	private void executeSyncDataTask() {
		progressTask();
		progressIndicator = packetHandlerController.getProgressIndicator();
		GridPane gridPane = homeController.getMainBox();
		gridPane.setDisable(true);
		progressIndicator.setVisible(true);
		Service<ResponseDTO> taskService = new Service<ResponseDTO>() {
			@Override
			protected Task<ResponseDTO> createTask() {
				return /**
						 * @author SaravanaKumar
						 *
						 */
				new Task<ResponseDTO>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected ResponseDTO call() {

						LOGGER.info("REGISTRATION - SYNC - HEADER_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
								"Handling all the sync activities");

						return jobConfigurationService.executeAllJobs();

					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();

		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				double totalJobs = (double)(jobConfigurationService.getActiveSyncJobMap().size()
						- jobConfigurationService.getOfflineJobs().size()
						- jobConfigurationService.getUnTaggedJobs().size());
				packetHandlerController.syncProgressBar.setProgress(BaseJob.successJob.size() / totalJobs);
				packetHandlerController.setLastUpdateTime();

				ResponseDTO responseDTO = taskService.getValue();
				boolean isLogoutRequired = false;
				
				if (responseDTO.getErrorResponseDTOs() != null) {
					generateAlert(RegistrationConstants.ERROR, responseDTO.getErrorResponseDTOs().get(0).getMessage()+"#TYPE#ERROR");
					isLogoutRequired = !responseDTO.getErrorResponseDTOs().isEmpty() && Objects.nonNull(responseDTO.getErrorResponseDTOs().get(0).getOtherAttributes());
				} else {
					generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SYNC_SUCCESS));
					isLogoutRequired = Objects.nonNull(responseDTO.getSuccessResponseDTO().getOtherAttributes()) && 
							responseDTO.getSuccessResponseDTO().getOtherAttributes().containsKey(RegistrationConstants.ROLES_MODIFIED);
				}
				if (isLogoutRequired) {
					showAlertAndLogout();
				}
				if (restartController.isToBeRestarted()) {
					/* Clear the completed job map */
					BaseJob.clearCompletedJobMap();
					/* Restart the application */
					restartController.restart();
				}
				gridPane.setDisable(false);
				boolean showAlert = false;
				if (validUser(showAlert))
					machineRemapCheck(showAlert);
				progressIndicator.setVisible(false);
			}
		});
		taskService.setOnFailed(event -> {
			gridPane.setDisable(false);
			boolean showAlert = false;
			if (validUser(showAlert))
				machineRemapCheck(showAlert);
			progressIndicator.setVisible(false);
		});
	}

	private void progressTask() {
		double totalJobs = (double)(jobConfigurationService.getActiveSyncJobMap().size()
				- jobConfigurationService.getOfflineJobs().size() - jobConfigurationService.getUnTaggedJobs().size());
		Service<String> progressTask = new Service<String>() {
			@Override
			protected Task<String> createTask() {
				BaseJob.successJob.clear();
				BaseJob.getCompletedJobMap().clear();
				return new Task<String>() {
					double success = 0;

					@Override
					protected String call() {
						while (BaseJob.getCompletedJobMap().size() != totalJobs) {
							success = BaseJob.successJob.size();
							packetHandlerController.syncProgressBar.setProgress(success / totalJobs);
						}
						return null;

					}
				};
			}
		};
		progressTask.start();
	}

	public void executeSoftwareUpdateTask(Pane pane, ProgressIndicator progressIndicator) {

		progressIndicator.setVisible(true);
		pane.setDisable(true);

		/**
		 * This anonymous service class will do the pre application launch task
		 * progress.
		 *
		 */
		Service<String> taskService = new Service<String>() {
			@Override
			protected Task<String> createTask() {
				return /**
						 * @author SaravanaKumar
						 *
						 */
				new Task<String>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected String call() {

						LOGGER.info("REGISTRATION - SOFTWARE_UPDATE - HEADER_CONTROLLER", APPLICATION_NAME,
								APPLICATION_ID, "Handling all the Software Update activities");

						progressIndicator.setVisible(true);
						pane.setDisable(true);
						return softwareUpdate();

					}
				};
			}
		};

		progressIndicator.progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {

				pane.setDisable(false);
				progressIndicator.setVisible(false);

				if (RegistrationConstants.ERROR.equalsIgnoreCase(taskService.getValue())) {
					// generateAlert(RegistrationConstants.ERROR,
					// RegistrationUIConstants.getMessageLanguageSpecific("UNABLE_TO_UPDATE);
					softwareUpdate(pane, progressIndicator, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_TO_UPDATE), true);
				} else if (RegistrationConstants.ALERT_INFORMATION.equalsIgnoreCase(taskService.getValue())) {
					// Update completed Re-Launch application
					generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UPDATE_COMPLETED));

					restartApplication();
				}

			}

		});

	}

	public void softwareUpdate(Pane pane, ProgressIndicator progressIndicator, String context,
			boolean isPreLaunchTaskToBeStopped) {
		Alert updateAlert = createAlert(AlertType.CONFIRMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UPDATE_AVAILABLE),
				RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.ALERT_NOTE_LABEL), context, RegistrationConstants.UPDATE_NOW_LABEL,
				RegistrationConstants.UPDATE_LATER_LABEL);

		pane.setDisable(true);

		updateAlert.show();
		Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();
		Double xValue = screenSize.getWidth() / 2 - updateAlert.getWidth() + 250;
		Double yValue = screenSize.getHeight() / 2 - updateAlert.getHeight();
		updateAlert.hide();
		updateAlert.setX(xValue);
		updateAlert.setY(yValue);
		updateAlert.showAndWait();

		/* Get Option from user */
		ButtonType result = updateAlert.getResult();
		if (result == ButtonType.OK) {

			softwareUpdateInitiate(pane, progressIndicator, context, isPreLaunchTaskToBeStopped);

		} else if (result == ButtonType.CANCEL && (statusValidatorService.isToBeForceUpdate())) {
			Alert alert = createAlert(AlertType.INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UPDATE_AVAILABLE),
					RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.ALERT_NOTE_LABEL), RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UPDATE_FREEZE_TIME_EXCEED),
					RegistrationConstants.UPDATE_NOW_LABEL, null);

			alert.show();
			Rectangle2D systemScreenSize = Screen.getPrimary().getVisualBounds();
			Double xPosValue = systemScreenSize.getWidth() / 2 - alert.getWidth() + 250;
			Double yPosValue = systemScreenSize.getHeight() / 2 - alert.getHeight();
			alert.hide();
			alert.setX(xPosValue);
			alert.setY(yPosValue);
			alert.showAndWait();

			/* Get Option from user */
			ButtonType alertResult = alert.getResult();

			if (alertResult == ButtonType.OK) {

				softwareUpdateInitiate(pane, progressIndicator, context, isPreLaunchTaskToBeStopped);
			}
		} else {
			pane.setDisable(false);
		}
	}

	private void softwareUpdateInitiate(Pane pane, ProgressIndicator progressIndicator, String context,
			boolean isPreLaunchTaskToBeStopped) {
		if (serviceDelegateUtil.isNetworkAvailable()) {
			executeSoftwareUpdateTask(pane, progressIndicator);
		} else {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.NO_INTERNET_CONNECTION));
			softwareUpdate(pane, progressIndicator, context, isPreLaunchTaskToBeStopped);
		}
	}

	/**
	 * This method closes the webcam, if opened, whenever the menu bar is clicked.
	 */
	public void closeOperations() {
		// webCameraController.closeWebcam();
	}

	public void executeDownloadPreRegDataTask(GridPane pane) {
		pane.setDisable(true);

		/**
		 * This anonymous service class will do the pre application launch task
		 * progress.
		 *
		 */
		Service<ResponseDTO> taskService = new Service<ResponseDTO>() {
			@Override
			protected Task<ResponseDTO> createTask() {
				return /**
						 * @author Yaswanth S
						 *
						 */
				new Task<ResponseDTO>() {
					/*
					 * (non-Javadoc)
					 *
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected ResponseDTO call() {
						LOGGER.info("REGISTRATION - HEADER_CONTROLLER - DOWNLOAD_PRE_REG_DATA_TASK", APPLICATION_NAME,
								APPLICATION_ID, "Started pre reg download task");

						Platform.runLater(() -> {
							try {
								packetHandlerController.setInProgressImage(getImage("in-progress.png", true));
							} catch (RegBaseCheckedException e) {
								LOGGER.error("Error in getting imageview: " + e);
							}
						});
						
						pane.setDisable(true);
						return jobConfigurationService.executeJob(RegistrationConstants.OPT_TO_REG_PDS_J00003,
								RegistrationConstants.JOB_TRIGGER_POINT_USER);
					}
				};
			}
		};

		taskService.start();

		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				LOGGER.info("REGISTRATION - HEADER_CONTROLLER - DOWNLOAD_PRE_REG_DATA_TASK", APPLICATION_NAME,
						APPLICATION_ID, "Completed pre reg download task");

				pane.setDisable(false);

				ResponseDTO responseDTO = taskService.getValue();

				if (responseDTO.getSuccessResponseDTO() != null) {
					SuccessResponseDTO successResponseDTO = responseDTO.getSuccessResponseDTO();
					generateAlertLanguageSpecific(successResponseDTO.getCode(), successResponseDTO.getMessage());

					packetHandlerController.setLastPreRegPacketDownloadedTime();
					packetHandlerController.setInProgressImage(null);
				} else if (responseDTO.getErrorResponseDTOs() != null) {
					ErrorResponseDTO errorresponse = responseDTO.getErrorResponseDTOs().get(0);
					generateAlertLanguageSpecific(errorresponse.getCode(), errorresponse.getMessage());
				}
			}
		});
		
		taskService.setOnFailed(event -> {
			pane.setDisable(false);
			packetHandlerController.setInProgressImage(null);
		});
	}

	/**
	 * Redirecting to PacketStatusSync Page
	 *
	 * @param event event for sync packet status
	 */
	public void userGuide(ActionEvent event) {
		userGuide.setOnAction(e -> {
			if (Desktop.isDesktopSupported()) {
				try {
					Desktop.getDesktop().browse(new URI(RegistrationConstants.MOSIP_URL));
				} catch (IOException ioException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
				} catch (URISyntaxException uriSyntaxException) {
					LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
							uriSyntaxException.getMessage() + ExceptionUtils.getStackTrace(uriSyntaxException));
				}
			}
		});
	}

	/**
	 * Redirects to mosip.io in case of user reset pword
	 * 
	 * @param event event for reset pword
	 */
	public void resetPword(ActionEvent event) {
		if (Desktop.isDesktopSupported()) {
			try {
				String url = ApplicationContext.getStringValueFromApplicationMap(RegistrationConstants.RESET_PWORD_URL);
				if (url.toUpperCase().contains(RegistrationConstants.EMAIL_PLACEHOLDER)) {
					UserDTO userDTO = loginService.getUserDetail(SessionContext.userId());
					url = url.replace(RegistrationConstants.EMAIL_PLACEHOLDER, userDTO.getEmail());
				}
				Desktop.getDesktop().browse(new URI(url));
			} catch (IOException ioException) {
				LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			} catch (URISyntaxException uriSyntaxException) {
				LOGGER.error(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						uriSyntaxException.getMessage() + ExceptionUtils.getStackTrace(uriSyntaxException));
			}
		}
	}

	private void machineRemapCheck(boolean showAlert) {
		if (centerMachineReMapService.isMachineRemapped()) {
			remapMachine();
		} else if (showAlert) {
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.REMAP_NOT_APPLICABLE));
		}
	}

	private boolean validUser(boolean showAlert) {
		if (SessionContext.getInstance() == null || !userDetailService.isValidUser(SessionContext.getInstance().getUserContext().getUserId())) {
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.USER_IN_ACTIVE));
			logout(null);
			return false;
		}
		return true;
	}

	public void openSettings() {
		getStage().getScene().getRoot().setDisable(true);
		settingsController.init(settingsByRole);
	}

	public void addShortCut(HBox shortCutHBox) {
		boolean isAdded = false;
		for (Node node : settingsHBox.getChildren()) {
			if (node != null && node.getId() != null && node.getId().equalsIgnoreCase(shortCutHBox.getId())) {
				isAdded = true;
			}
		}
		if (!isAdded) {
			localConfigService.updateShortcutPreference(RegistrationConstants.DEVICES_SHORTCUT_PREFERENCE_NAME,
					RegistrationConstants.ENABLE);
			ContextMenu contextMenu = new ContextMenu();
			MenuItem removeShortCut = new MenuItem("Remove Shortcut");
			contextMenu.getItems().add(removeShortCut);
			removeShortCut.setOnAction(event -> {
				localConfigService.updateShortcutPreference(RegistrationConstants.DEVICES_SHORTCUT_PREFERENCE_NAME,
						RegistrationConstants.DISABLE);
				settingsHBox.getChildren().remove(shortCutHBox);
				
			});
			shortCutHBox.setOnContextMenuRequested(e -> {
				contextMenu.show(shortCutHBox.getScene().getWindow(), e.getScreenX(), e.getScreenY());
			});
			settingsHBox.getChildren().add(shortCutHBox);
		}
	}
}