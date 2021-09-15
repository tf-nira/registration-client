package registrationtest.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.testfx.api.FxRobot;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import registrationtest.controls.Alerts;

import registrationtest.utility.PropertiesUtil;
import registrationtest.utility.WaitsUtil;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.http.ContentType;

public class BioCorrectionPage {

    private static final Logger logger = LogManager.getLogger(BioCorrectionPage.class);
    FxRobot robot;
    TextField additionalInfoTextBox;
    String additionalInfoRequestId = "#additionalInfoRequestId";
    WaitsUtil waitsUtil;

    public BioCorrectionPage(FxRobot robot) {
        this.robot = robot;
        waitsUtil = new WaitsUtil(robot);

    }

    public void setAdditionalInfoRequestId(String value) {

        logger.info("set additional info ");

        try {
            additionalInfoTextBox = waitsUtil.lookupByIdTextField(additionalInfoRequestId, robot);

            assertNotNull(additionalInfoTextBox, "additionalInfoTextBox Not Present");

            additionalInfoTextBox.setText(value);

        } catch (Exception e) {
            logger.error("", e);
        }

    }

    public void setMDSscore(String qualityScore) {

        try {
            String requestBody = "{\"type\":\"Biometric Device\",\"qualityScore\":\"" + qualityScore
                    + "\",\"fromIso\":false}";

            Response response = RestAssured.given().baseUri("http://127.0.0.1:4501/admin/score")
                    .contentType(ContentType.JSON).and().body(requestBody).when().post().then().extract().response();

            assertEquals(200, response.statusCode());
            assertEquals("Success", response.jsonPath().getString("errorInfo"));
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public void setMDSprofile(String type) {
        try {
            String requestBody = "{\"type\":\"Biometric Device\",\"profileId\":\"" + type + "\"}";

            Response response = RestAssured.given().baseUri("http://127.0.0.1:4501/admin/profile")
                    .contentType(ContentType.JSON).and().body(requestBody).when().post().then().extract().response();
            assertEquals(200, response.statusCode());
            assertEquals("Success", response.jsonPath().getString("errorInfo"));

        } catch (Exception e) {
            logger.error("", e);
        }
    }

}