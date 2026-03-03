package io.mosip.registration.util.control.impl;

import java.util.*;
import java.util.Map.Entry;

import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.dao.MasterSyncDao;
import org.springframework.context.ApplicationContext;

import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.controller.FXUtils;
import io.mosip.registration.controller.GenericController;
import io.mosip.registration.controller.Initialization;
import io.mosip.registration.controller.reg.Validations;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.entity.Location;
import io.mosip.registration.service.sync.MasterSyncService;
import io.mosip.registration.util.common.ComboBoxAutoComplete;
import io.mosip.registration.util.common.DemographicChangeActionHandler;
import io.mosip.registration.util.control.FxControl;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.springframework.util.Assert;

public class DropDownFxControl extends FxControl {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(DropDownFxControl.class);
	private static final String loggerClassName = "DropDownFxControl";
	private int hierarchyLevel;
	private Validations validation;
	private DemographicChangeActionHandler demographicChangeActionHandler;
	private MasterSyncService masterSyncService;
	private MasterSyncDao masterSyncDao;
	
	 Map<String, String> statusDistrictMap = Map.of(
             RegistrationConstants.RESIDENCE_STATUS, RegistrationConstants.RESIDENCE_DISTRICT,
             RegistrationConstants.BIRTH_STATUS, RegistrationConstants.BIRTH_DISTRICT,
             RegistrationConstants.ORIGIN_STATUS, RegistrationConstants.ORIGIN_DISTRICT,
             RegistrationConstants.ENROLMENT_STATUS, RegistrationConstants.ENROLLMENT_DISTRICT
     );
	 
	 private static final List<String> SECTION_FIRST_FIELDS = List.of(
			 RegistrationConstants.EMPLOYER_NAME,
		     RegistrationConstants.NAME_OF_SCHOOL,
		     RegistrationConstants.OTHERCHILD,
			 RegistrationConstants.PRINCIPAL_OF_AIN
	);
	 
	 private static final Map<String, Set<String>> VISIBILITY_SECTION = Map.of(
		    RegistrationConstants.STUDENT_PASS, Set.of(RegistrationConstants.NAME_OF_SCHOOL),
		    RegistrationConstants.DP, Set.of(RegistrationConstants.PRINCIPAL_OF_AIN),
		    "DEFAULT",
			 Set.of(RegistrationConstants.EMPLOYER_NAME,
					 RegistrationConstants.OTHERCHILD)
	);


	public DropDownFxControl() {
		ApplicationContext applicationContext = ClientApplication.getApplicationContext();
		validation = applicationContext.getBean(Validations.class);
		demographicChangeActionHandler = applicationContext.getBean(DemographicChangeActionHandler.class);
		masterSyncService = applicationContext.getBean(MasterSyncService.class);
		masterSyncDao  = applicationContext.getBean(MasterSyncDao.class);
	}

	@Override
	public FxControl build(UiFieldDTO uiFieldDTO) {
		this.uiFieldDTO = uiFieldDTO;
		this.control = this;
		this.node = create(uiFieldDTO,
				getRegistrationDTo().getSelectedLanguagesByApplicant().get(0));
//As subType in UI Spec is defined in any lang we find the langCode to fill initial dropdown
		String subTypeLangCode = getSubTypeLangCode(uiFieldDTO.getSubType());
		if(subTypeLangCode != null) {
			TreeMap<Integer, List<String>> groupFields =
					GenericController.currentHierarchyMap.getOrDefault(uiFieldDTO.getGroup(), new TreeMap<>());
			for (Entry<Integer, List<String>> entry :
					GenericController.hierarchyLevels.get(subTypeLangCode).entrySet()) {
				if (entry.getValue().contains(uiFieldDTO.getSubType())) {
					this.hierarchyLevel = entry.getKey();
					groupFields.computeIfAbsent(entry.getKey(), k -> new
							ArrayList<>()).add(uiFieldDTO.getId());
					GenericController.currentHierarchyMap.put(uiFieldDTO.getGroup(), groupFields);
					break;
				}
			}
		}
		Map<String, Object> data = new LinkedHashMap<>();
		data.put(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0),
				getPossibleValues(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0)));
//clears & refills items
		fillData(data);
		return this.control;
	}

	private String getSubTypeLangCode(String subType) {
		for( String langCode : GenericController.hierarchyLevels.keySet()) {
			TreeMap<Integer, List<String>> levels =	GenericController.hierarchyLevels.get(langCode);
			for(Entry<Integer, List<String>> hierarchy: levels.entrySet())
				if( hierarchy.getValue().contains(subType))
					return langCode;
		}
		return null;
	}

	private VBox create(UiFieldDTO uiFieldDTO, String langCode) {
		String fieldName = uiFieldDTO.getId();

		/** Container holds title, fields and validation message elements */
		VBox simpleTypeVBox = new VBox();
		//simpleTypeVBox.setPrefWidth(200);
		//simpleTypeVBox.setPrefHeight(95);
		simpleTypeVBox.setSpacing(5);
		simpleTypeVBox.setId(fieldName + RegistrationConstants.VBOX);

		/** Title label */
		Label fieldTitle = (Label) getLabel(uiFieldDTO.getId() + RegistrationConstants.LABEL, "",
				RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL, true, simpleTypeVBox.getWidth());
		simpleTypeVBox.getChildren().add(fieldTitle);

		List<String> labels = new ArrayList<>();
		getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(lCode -> {
			labels.add(this.uiFieldDTO.getLabel().get(lCode));
		});

		String titleText = String.join(RegistrationConstants.SLASH, labels) + getMandatorySuffix(uiFieldDTO);
		ComboBox<GenericDto> comboBox = getComboBox(fieldName, titleText, RegistrationConstants.DOC_COMBO_BOX,
				simpleTypeVBox.getPrefWidth(), false);
		comboBox.setMaxWidth(Double.MAX_VALUE);
		simpleTypeVBox.getChildren().add(comboBox);

		comboBox.setOnMouseExited(event -> {
			getField(uiFieldDTO.getId() + RegistrationConstants.MESSAGE).setVisible(false);
			if(comboBox.getTooltip()!=null) {
			comboBox.getTooltip().hide();
			}
		});

		comboBox.setOnMouseEntered((event -> {
			getField(uiFieldDTO.getId() + RegistrationConstants.MESSAGE).setVisible(true);

		}));

		setListener(comboBox);

		fieldTitle.setText(titleText);
		Label messageLabel = (Label) getLabel(uiFieldDTO.getId() + RegistrationConstants.MESSAGE, null,
				RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL, false, simpleTypeVBox.getPrefWidth());
		messageLabel.setMaxWidth(200);
		simpleTypeVBox.getChildren().add(messageLabel);

		changeNodeOrientation(simpleTypeVBox, langCode);

		return simpleTypeVBox;
	}


	public List<GenericDto> getPossibleValues(String langCode) {
		boolean isHierarchical = false;
		String fieldSubType = uiFieldDTO.getSubType();
		if (GenericController.currentHierarchyMap.containsKey(uiFieldDTO.getGroup())) {
			isHierarchical = true;
			Entry<Integer, List<String>> parentEntry =
					GenericController.currentHierarchyMap.get(uiFieldDTO.getGroup())
							.lowerEntry(this.hierarchyLevel);
			if (parentEntry == null) { //first parent
				parentEntry =
						GenericController.hierarchyLevels.get(langCode).lowerEntry(this.hierarchyLevel);
				Assert.notNull(parentEntry);
				List<Location> locations =
						masterSyncDao.getLocationDetails(parentEntry.getValue().get(0), langCode);
				fieldSubType = locations != null && !locations.isEmpty() ?
						locations.get(0).getCode() : null;
			} else {
				for (String parent : parentEntry.getValue()) {
					FxControl fxControl =
							GenericController.getFxControlMap().get(parent);
					Node comboBox = getField(fxControl.getNode(), parent);
					GenericDto selectedItem = comboBox != null ?
							((ComboBox<GenericDto>)
									comboBox).getSelectionModel().getSelectedItem() : null;
					fieldSubType = selectedItem != null ? selectedItem.getCode() :
							null;
					if (fieldSubType != null) {
						List<GenericDto> values =
								masterSyncService.getFieldValues(fieldSubType, uiFieldDTO.getSubType(), langCode, isHierarchical);
						if (!values.isEmpty())
							return values;
					}
				}
				if (fieldSubType == null) {
					return Collections.EMPTY_LIST;
				}
			}
		}
		return masterSyncService.getFieldValues(fieldSubType, uiFieldDTO.getSubType(),
				langCode, isHierarchical);
	}

	private <T> ComboBox<GenericDto> getComboBox(String id, String titleText, String stycleClass, double prefWidth,
			boolean isDisable) {
		ComboBox<GenericDto> field = new ComboBox<GenericDto>();
		StringConverter<T> uiRenderForComboBox = FXUtils.getInstance().getStringConverterForComboBox();
		field.setId(id);
		// field.setPrefWidth(prefWidth);

		//field.setPromptText(titleText);
		field.setDisable(isDisable);
		field.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_COMBOBOX);
		field.setConverter((StringConverter<GenericDto>) uiRenderForComboBox);
		return field;
	}


	@Override
	public void setData(Object data) {
		ComboBox<GenericDto> appComboBox = (ComboBox<GenericDto>) getField(uiFieldDTO.getId());
		if(appComboBox.getSelectionModel().getSelectedItem() == null) {
			return;
		}

		String selectedCode = appComboBox.getSelectionModel().getSelectedItem().getCode();
		switch (this.uiFieldDTO.getType()) {
			case RegistrationConstants.SIMPLE_TYPE:
				List<SimpleDto> values = new ArrayList<SimpleDto>();
				for (String langCode : getRegistrationDTo().getSelectedLanguagesByApplicant()) {
					if(langCode.equals(appComboBox.getSelectionModel().getSelectedItem().getLangCode())) {
						SimpleDto simpleDto = new SimpleDto(langCode, appComboBox.getSelectionModel().getSelectedItem().getName());
						values.add(simpleDto);
					}
					else {
						Optional<GenericDto> result = getPossibleValues(langCode).stream()
								.filter(b -> b.getCode().equals(selectedCode)).findFirst();
						if (result.isPresent()) {
							SimpleDto simpleDto = new SimpleDto(langCode, result.get().getName());
							values.add(simpleDto);
						}
					}
				}
				getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), values);
				getRegistrationDTo().SELECTED_CODES.put(uiFieldDTO.getId()+"Code", selectedCode);
				
				if(uiFieldDTO.getId().equalsIgnoreCase(RegistrationConstants.FACILITY_TYPE)) {
					String fcValue = null;
					Object facilityType = getRegistrationDTo().getDemographics().get(RegistrationConstants.FACILITY_TYPE);
			        if (facilityType instanceof List<?>) {
			            List<?> facilityTypeList = (List<?>) facilityType;
			            if (!facilityTypeList.isEmpty() && facilityTypeList.get(0) instanceof SimpleDto) {
			                SimpleDto dto = (SimpleDto) facilityTypeList.get(0);
			                if (dto.getValue() != null) {
			                	fcValue = dto.getValue().trim(); // Normalize
			                }
			            }
			        }
					updateFacilityCategory(fcValue);
					updateFacilitySubCategory(fcValue);
					handleEmployeeandSchoolSection(fcValue);
				}
				
				String districtField = statusDistrictMap.get(uiFieldDTO.getId());
	            if (districtField != null) {
	                handleStatusandDistrictValue(uiFieldDTO.getId(), districtField);
	            }
	            
				break;
			default:
				Optional<GenericDto> result = getPossibleValues(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0)).stream()
						.filter(b -> b.getCode().equals(selectedCode)).findFirst();
				if (result.isPresent()) {
					getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), result.get().getName());
					getRegistrationDTo().SELECTED_CODES.put(uiFieldDTO.getId()+"Code", selectedCode);
				}
				break;
		}
	}

	private void handleEmployeeandSchoolSection(String fcValue) {
	    if (fcValue == null) 
	    	return;

	    Set<String> visibleFields = VISIBILITY_SECTION
	            .getOrDefault(fcValue, VISIBILITY_SECTION.get("DEFAULT"));

	    for (String fieldId : SECTION_FIRST_FIELDS) {
	        if (visibleFields.contains(fieldId)) {
	            showSectionByAnyField(fieldId);
	        } else {
	            hideSectionByAnyField(fieldId);
	        }
	    }
	}


	private void hideSectionByAnyField(String fieldId) {
	    FxControl fx = GenericController.getFxControlMap().get(fieldId);
	    if (fx != null && fx.getNode() != null) {
	        Node section = fx.getNode().getParent();
	        if (section != null) {
	            section.setVisible(false);
	            section.setManaged(false);
	        }
	    }
	}

	private void showSectionByAnyField(String fieldId) {
	    FxControl fx = GenericController.getFxControlMap().get(fieldId);
	    if (fx != null && fx.getNode() != null) {
	        Node section = fx.getNode().getParent();
	        if (section != null) {
	            section.setVisible(true);
	            section.setManaged(true);
	        }
	    }
	}

	private void handleStatusandDistrictValue(String statusField, String districtField) {
		String resValue = null; 
		Object residenceStatus = getRegistrationDTo().getDemographics().get(statusField); 
		if (residenceStatus instanceof List<?>) { 
			List<?> residenceStatusList = (List<?>) residenceStatus; 
			if (!residenceStatusList.isEmpty() && residenceStatusList.get(0) instanceof SimpleDto) { 
				SimpleDto dto = (SimpleDto) residenceStatusList.get(0); 
				if (dto.getValue() != null) { 
					resValue = dto.getValue().trim().toLowerCase(); // Normalize } } }
				}
			}
		}
	    updateDistrictList(resValue, districtField);
	}

	public void updateDistrictList(String status, String districtField) {
		
        if (status != null) {
            FxControl fxControl = getFxControl(districtField);
            String langCode = getRegistrationDTo().getSelectedLanguagesByApplicant().get(0);

            List<GenericDto> filteredValues = masterSyncService.getFilteredFieldValues(
                    RegistrationConstants.UGA, RegistrationConstants.DISTRICT, langCode, true, status);
            
            Map<String, Object> dataList = new LinkedHashMap<>();
            dataList.put(langCode, filteredValues);
            fxControl.fillData(dataList);
        }
	}
	
	private void updateFacilityCategory(String facilityType) {
        if (facilityType != null) {
            FxControl fxControl = getFxControl(RegistrationConstants.FACILITY_TYPE_CATEGORY);
            String langCode = getRegistrationDTo().getSelectedLanguagesByApplicant().get(0);

            List<GenericDto> filteredValues = masterSyncService.getFacilityTypeCategoryAndSubCategoryValues(
            		RegistrationConstants.FACILITY_TYPE_CATEGORY, langCode, facilityType);
            
            Map<String, Object> dataList = new LinkedHashMap<>();
            dataList.put(langCode, filteredValues);
            fxControl.fillData(dataList);
        }
	}
	
	private void updateFacilitySubCategory(String facilityType) {
        if (facilityType != null) {
            FxControl fxControl = getFxControl(RegistrationConstants.FACILITY_TYPE_SUB_CATEGORY);
            String langCode = getRegistrationDTo().getSelectedLanguagesByApplicant().get(0);

            List<GenericDto> filteredValues = masterSyncService.getFacilityTypeCategoryAndSubCategoryValues(
            		RegistrationConstants.FACILITY_SUB_CATEGORY_SUBTYPE, langCode, facilityType);
            
            Map<String, Object> dataList = new LinkedHashMap<>();
            dataList.put(langCode, filteredValues);
            fxControl.fillData(dataList);
        }
	}

	@Override
	public Object getData() {
		return getRegistrationDTo().getDemographics().get(uiFieldDTO.getId());
	}

	@Override
	public boolean isValid() {
	    ComboBox<GenericDto> appComboBox = (ComboBox<GenericDto>) getField(uiFieldDTO.getId());
	    boolean isValid = appComboBox != null && appComboBox.getSelectionModel().getSelectedItem() != null;
	    boolean isRequiredField = requiredFieldValidator.isRequiredField(this.uiFieldDTO, getRegistrationDTo());
	    if (appComboBox != null) {
	    	appComboBox.getStyleClass().removeIf((s) -> {
				return s.equals("demographicComboboxFocused");
			});
	        if (!isValid && (uiFieldDTO.isRequired() || isRequiredField)) { 
	            appComboBox.getStyleClass().add("demographicComboboxFocused"); 
	        }
	    }
	    return isValid;
	}

	@Override
	public boolean isEmpty() {
		ComboBox<GenericDto> appComboBox = (ComboBox<GenericDto>) getField(uiFieldDTO.getId());
		return appComboBox == null || appComboBox.getSelectionModel().getSelectedItem() == null;
	}

	@Override
	public void setListener(Node node) {
		ComboBox<GenericDto> fieldComboBox = (ComboBox<GenericDto>) node;
		fieldComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
			displayFieldLabel();
			if (isValid()) {

				List<String> toolTipText = new ArrayList<>();
				String selectedCode = fieldComboBox.getSelectionModel().getSelectedItem().getCode();
				for (String langCode : getRegistrationDTo().getSelectedLanguagesByApplicant()) {
					if(langCode.equals(fieldComboBox.getSelectionModel().getSelectedItem().getLangCode())) {
						toolTipText.add(fieldComboBox.getSelectionModel().getSelectedItem().getName());
					}
					else {
						Optional<GenericDto> result = getPossibleValues(langCode).stream()
								.filter(b -> b.getCode().equals(selectedCode)).findFirst();
						if (result.isPresent()) {
							
							toolTipText.add(result.get().getName());
						}
					}
				}

				Label messageLabel = (Label) getField(uiFieldDTO.getId() + RegistrationConstants.MESSAGE);
				messageLabel.setText(String.join(RegistrationConstants.SLASH, toolTipText));

				setData(null);
				refreshNextHierarchicalFxControls();
				demographicChangeActionHandler.actionHandle((Pane) getNode(), node.getId(),	uiFieldDTO.getChangeAction());
				
				// Group level visibility listeners
				if(uiFieldDTO.getDependentFields() != null && !uiFieldDTO.getDependentFields().isEmpty()) {
					refreshDependentFields(uiFieldDTO.getDependentFields());
				}
				
				//reset the value
				if (uiFieldDTO.isSetRequired()){
					resetValue();
				}

				if(uiFieldDTO.getId().equalsIgnoreCase(RegistrationConstants.PRIMARY_NATIONALITY) || uiFieldDTO.getId().equalsIgnoreCase(RegistrationConstants.SECONDARY_NATIONALITY)) {
					GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);
					boolean nationalityCheck = genericController.validateSameNationality();
					FxControl fxControl = getFxControl(uiFieldDTO.getId());
					FxControl fxControl1 = getFxControl(RegistrationConstants.PRIMARY_NATIONALITY);
					if(!nationalityCheck) {
						fxControl.setMessage(RegistrationConstants.SAME_NATIONALITY_ERROR_MSG);
					} else {
						fxControl.setMessage(null);
					}
				}

				List<String> fieldHierarchy = List.of(
						RegistrationConstants.ENROLLMENT_DISTRICT,
						RegistrationConstants.ENROLLMENT_COUNTY,
						RegistrationConstants.ENROLLMENT_SUB_COUNTY,
						RegistrationConstants.ENROLLMENT_PARISH,
						RegistrationConstants.ENROLLMENT_VILLAGE
					);

					String changedFieldId = uiFieldDTO.getId();
					int changedIndex = fieldHierarchy.indexOf(changedFieldId);

					if (changedIndex != -1 && changedIndex < fieldHierarchy.size() - 1) {
					    for (int i = changedIndex + 1; i < fieldHierarchy.size(); i++) {
					        getRegistrationDTo().removeDemographicField(fieldHierarchy.get(i));
					    }
					}


				if(uiFieldDTO.getId().equalsIgnoreCase("gender")){
					FxControl fxControl1 =  getFxControl("maritalStatus");
					FxControl fxControl2 =  getFxControl("numberOfOtherSpouses");
					FxControl fxControl3 =  getFxControl("numberOfOtherSpousesAlien");
					fxControl1.selectAndSet(null);
					fxControl1.setData(null);
					fxControl1.getNode().setDisable(false);
					if(fxControl2 != null) {
						fxControl2.selectAndSet(null);
						fxControl2.setData(null);
						fxControl2.getNode().setDisable(false);
					} else if(fxControl3 != null) {
						fxControl3.selectAndSet(null);
						fxControl3.setData(null);
						fxControl3.getNode().setDisable(false);
					}
				}

				if(uiFieldDTO.getId().equalsIgnoreCase("maritalStatus")){
					GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);
					Map<String, Object> demographics = genericController.getRegistrationDTOFromSession().getDemographics();
					SimpleDto genderData = (SimpleDto) ((ArrayList) demographics.get("gender")).get(0);
					SimpleDto maritalStatusData = (SimpleDto) ((ArrayList) demographics.get("maritalStatus")).get(0);
					FxControl fxControl1 =  getFxControl("numberOfOtherSpouses");
					FxControl fxControl2 =  getFxControl("numberOfOtherSpousesAlien");
					if (genderData.getValue().equalsIgnoreCase("Female") && !(maritalStatusData.getValue().equalsIgnoreCase("Single"))) {
						if(fxControl1 != null) {
							fxControl1.selectAndSet("1");
							fxControl1.setData("1");
							fxControl1.getNode().setDisable(true);
						} else if(fxControl2 != null) {
							fxControl2.selectAndSet("1");
							fxControl2.setData("1");
							fxControl2.getNode().setDisable(true);
						}
					}
					else {
						if(fxControl1 != null) {
							fxControl1.selectAndSet(null);
							fxControl1.setData(null);
							fxControl1.getNode().setDisable(false);
						} else if(fxControl2 != null) {
							fxControl2.selectAndSet(null);
							fxControl2.setData(null);
							fxControl2.getNode().setDisable(false);
						}
					}
				}


				Map<String, String> fieldMappings = Map.of("residenceStatus", "appResCountryUGA", "applicantBirthPlace", "appBirCountryUGA",
					    "applicantOriginPlace", "appOriCountryUGA", "fatherResidence","fatResCountryUGA", "fatherOrigin","fatOriCountryUGA",
					    "motherResidence","motResCountryUGA", "motherOrigin","motOriCountryUGA", "guardianResidence", "guardiansCountry");

				if (fieldMappings.containsKey(uiFieldDTO.getId())) {
				    GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);
				    Map<String, Object> demographics = genericController.getRegistrationDTOFromSession().getDemographics();
				    List<SimpleDto> residenceDataList = (List<SimpleDto>) demographics.get(uiFieldDTO.getId());
				    if (residenceDataList != null && !residenceDataList.isEmpty()) {
				        SimpleDto residenceData = residenceDataList.get(0);
				        FxControl fxControl = getFxControl(fieldMappings.get(uiFieldDTO.getId()));
				        if ("In Uganda".equalsIgnoreCase(residenceData.getValue())) {
				            fxControl.selectAndSet("UGA");
				            fxControl.setData("UGA");
				            fxControl.getNode().setDisable(true);
				        }
				    }
				}
				
				if (uiFieldDTO.getId().equalsIgnoreCase(RegistrationConstants.CARD_REQUIRED)) {
				    GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);
				    String cardValue = genericController.getRegistrationDTOFromSession().getDemographic(RegistrationConstants.CARD_REQUIRED);
				    Set<String> copCat = Set.of(
					        "familyInformationCat",
					        "citizenshipTypeCat"
					);
				    
				    // Get demographics list
				    Map<String, Object> demographics = genericController.getRegistrationDTOFromSession().getDemographics();
				    
				    // Check if any copCat field has value "Y"
			        boolean anyCopCatFieldHasY = demographics.entrySet().stream()
			        	    .anyMatch(e -> copCat.contains(e.getKey()) && "Y".equals(String.valueOf(e.getValue())));
			        
			        FxControl fxControl = getFxControl(uiFieldDTO.getId()); // Assuming you have a FxControl store
			        if (fxControl != null) {
			        	if ("Yes".equalsIgnoreCase(cardValue) && anyCopCatFieldHasY && !fxControl.getNode().isDisable()) {
			                fxControl.setMessage("This is subject to card change charges");
			            } else {
			                fxControl.setMessage(null); // or use null if your method handles that safely
			            }
			        }
				}

				if(uiFieldDTO.getId().equalsIgnoreCase("declarant")) {

					GenericController genericController = ClientApplication.getApplicationContext().getBean(GenericController.class);

					List<String> declarantFieldIds = new ArrayList<>(List.of(new String[]{"declarantSurname", "declarantgivenName", "declarantotherNames", "declarantPreviousNames", "introducerNIN"}));
					List<String> fatherFieldIds = List.of(new String[]{"fatherSurname", "fatherGivenName", "fatherOtherNames", "fatherPreviousName", "fatherNIN"});
					List<String> motherFieldIds = List.of(new String[]{"motherSurname", "motherGivenName", "motherOtherNames", "motherPreviousName", "motherNIN"});

					if (newValue.getName().equalsIgnoreCase("Father")){
						for (int i=0; i<5; i++) {
							FxControl fxControl = getFxControl(declarantFieldIds.get(i));
							fxControl.selectAndSet(null);
							fxControl.setData(null);
							fxControl.getNode().setDisable(false);
							Object fatherDataObject = genericController.getRegistrationDTOFromSession().getDemographics().get(fatherFieldIds.get(i));
							if(fatherDataObject!=null) {
								fxControl.selectAndSet(fatherDataObject);
								fxControl.setData(fatherDataObject);
								fxControl.getNode().setDisable(true);
							}
						}

						Map<String, Object> demographics = genericController.getRegistrationDTOFromSession().getDemographics();

						// Residence Status
						SimpleDto residenceData = (SimpleDto) ((ArrayList) demographics.get("fatherResidence")).get(0);
						FxControl fxControl1 =  getFxControl("declarantResidenceStatus");
						if (residenceData.getValue().equalsIgnoreCase("In Uganda")) {
							fxControl1.selectAndSet("UGA");
							fxControl1.setData("UGA");
						}
						else {
							fxControl1.selectAndSet("FRN");
							fxControl1.setData("FRN");
						}
						fxControl1.getNode().setDisable(true);

						// Gender
						FxControl fxControl2 =  getFxControl("declarantGender");
						fxControl2.selectAndSet("MLE");
						fxControl2.setData("MLE");
						fxControl2.getNode().setDisable(true);
					}
					else if (newValue.getName().equalsIgnoreCase("Mother")) {
						for (int i=0; i<5; i++) {
							FxControl fxControl = getFxControl(declarantFieldIds.get(i));
							fxControl.selectAndSet(null);
							fxControl.setData(null);
							fxControl.getNode().setDisable(false);
							Object motherDataObject = genericController.getRegistrationDTOFromSession().getDemographics().get(motherFieldIds.get(i));
							if(motherDataObject!=null) {
								fxControl.selectAndSet(motherDataObject);
								fxControl.setData(motherDataObject);
								fxControl.getNode().setDisable(true);
							}
						}

						Map<String, Object> demographics = genericController.getRegistrationDTOFromSession().getDemographics();

						// Gender
						FxControl fxControl3 =  getFxControl("declarantGender");
						fxControl3.selectAndSet("FLE");
						fxControl3.setData("FLE");
						fxControl3.getNode().setDisable(true);


						// Maiden Name
						FxControl fxControl1 =  getFxControl("declarantMaidenName");
						fxControl1.selectAndSet(demographics.get(null));
						fxControl1.setData(demographics.get(null));
						fxControl1.getNode().setDisable(false);
						fxControl1.selectAndSet(demographics.get("motherMaidenName"));
						fxControl1.setData(demographics.get("motherMaidenName"));
						fxControl1.getNode().setDisable(true);

						// Residence Status
						SimpleDto residenceData = (SimpleDto) ((ArrayList) demographics.get("motherResidence")).get(0);
						FxControl fxControl2 =  getFxControl("declarantResidenceStatus");
						if (residenceData.getValue().equalsIgnoreCase("In Uganda")) {
							fxControl2.selectAndSet("UGA");
							fxControl2.setData("UGA");
						}
						else {
							fxControl2.selectAndSet("FRN");
							fxControl2.setData("FRN");
						}
						fxControl2.getNode().setDisable(true);
					}
					else {
						declarantFieldIds.add("declarantMaidenName");
						declarantFieldIds.add("declarantGender");
						declarantFieldIds.add("declarantResidenceStatus");

						for(String fieldId: declarantFieldIds) {
							FxControl fxControl = getFxControl(fieldId);
							fxControl.selectAndSet(null);
							fxControl.setData(null);
							fxControl.getNode().setDisable(false);
						}
					}
				}

			}
		});
	}
	
	@Override
	public void clearToolTipText() {
		Label messageLabel = (Label) getField(uiFieldDTO.getId() + RegistrationConstants.MESSAGE);
		messageLabel.setText(null);
	}

	private void refreshNextHierarchicalFxControls() {
		if(GenericController.currentHierarchyMap.containsKey(uiFieldDTO.getGroup())) {
			Entry<Integer, List<String>> nextEntry =
					GenericController.currentHierarchyMap.get(uiFieldDTO.getGroup())
							.higherEntry(this.hierarchyLevel);
			while (nextEntry != null) {
				for(String fieldLevel: nextEntry.getValue()) {
					FxControl fxControl =
							GenericController.getFxControlMap().get(fieldLevel);
					Map<String, Object> data = new LinkedHashMap<>();
					data.put(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0),
							fxControl.getPossibleValues(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0)));
//clears & refills items
					fxControl.fillData(data);
				}
				nextEntry =
						GenericController.currentHierarchyMap.get(uiFieldDTO.getGroup())
								.higherEntry(nextEntry.getKey());
			}
		}
	}

	private void displayFieldLabel() {
		FXUtils.getInstance().toggleUIField((Pane) getNode(), uiFieldDTO.getId() + RegistrationConstants.LABEL,
				true);
		Label label = (Label) getField(uiFieldDTO.getId() + RegistrationConstants.LABEL);
		label.getStyleClass().add("demoGraphicFieldLabelOnType");
		label.getStyleClass().remove("demoGraphicFieldLabel");
		FXUtils.getInstance().toggleUIField((Pane) getNode(), uiFieldDTO.getId() + RegistrationConstants.MESSAGE, false);
	}



	private Node getField(Node fieldParentNode, String id) {
		return fieldParentNode.lookup(RegistrationConstants.HASH + id);
	}

	@Override
	public void fillData(Object data) {
		ComboBox<GenericDto> comboBox = (ComboBox<GenericDto>) getField(uiFieldDTO.getId());
		
		comboBox.getItems().clear();
		comboBox.setValue(null);
		clearToolTipText();
		
		if (data != null) {
			
			Map<String, List<GenericDto>> val = (Map<String, List<GenericDto>>) data;

			List<GenericDto> items = val.get(getRegistrationDTo().getSelectedLanguagesByApplicant().get(0));

			if (items != null && !items.isEmpty()) {
				setItems(comboBox, items);  // Fill with new data
			}

		}
	}

	private void setItems(ComboBox<GenericDto> comboBox, List<GenericDto> val) {
		if (comboBox != null && val != null && !val.isEmpty()) {
			comboBox.getItems().clear();
			comboBox.getItems().addAll(val);

			new ComboBoxAutoComplete<GenericDto>(comboBox);
			
			comboBox.hide();

		}
	}

	@Override
	public void selectAndSet(Object data) {
		ComboBox<GenericDto> field = (ComboBox<GenericDto>) getField(uiFieldDTO.getId());
		if (data == null) {
			field.getSelectionModel().clearSelection();
			return;
		}

		if (data instanceof List) {

			List<SimpleDto> list = (List<SimpleDto>) data;

			selectItem(field, list.isEmpty() ? null : list.get(0).getValue());

		} else if (data instanceof String) {

			selectItem(field, (String) data);
		}
	}

	private void selectItem(ComboBox<GenericDto> field, String val) {
		if (field != null && val != null && !val.isEmpty()) {
			for (GenericDto genericDto : field.getItems()) {
				if (genericDto.getCode().equals(val)) {
					field.getSelectionModel().select(genericDto);
					break;
				}
			}
		}
	}
}
