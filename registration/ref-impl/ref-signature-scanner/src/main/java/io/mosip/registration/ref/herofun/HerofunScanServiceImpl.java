package io.mosip.registration.ref.herofun;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import io.mosip.registration.api.signaturescanner.SignatureService;

@Component
public class HerofunScanServiceImpl implements SignatureService {

	private static final Logger LOGGER = LoggerFactory.getLogger(HerofunScanServiceImpl.class);

	@Override
	public String getServiceName() {
		return "HeroFun SignPad";
	}

	@Override
	public BufferedImage scan(DocScanDevice docScanDevice, String deviceType) {

		SignaturePad signaturepad = SignaturePad.INSTANCE;
		BufferedImage bufferedImage = null;
		if (signaturepad.HWInit(docScanDevice.getWidth(), docScanDevice.getHeight()) == 1) {
			try {
				bufferedImage = getSignatureImage(signaturepad);
				signaturepad.HWClearSig();
			} catch (InterruptedException e) {
				LOGGER.error("Failed to scan", e);
				return null;
			} catch (ExecutionException e) {
				LOGGER.error("Failed to scan", e);
				return null;
			}catch (Exception e) {
				LOGGER.error("Failed to scan", e);
				return null;
			}
			
		}
		return bufferedImage;
	}

	@Override
	public List<DocScanDevice> getConnectedDevices(String enabled) {
		SignaturePad signaturepad = SignaturePad.INSTANCE;
		LOGGER.info("JNA loader path--------------" + System.getProperty("jna.library.path"));
		List<DocScanDevice> devices = new ArrayList<>();
		if(signaturepad.HWGetDeviceStatus()==1) {
			DocScanDevice docScanDevice = new DocScanDevice();
			docScanDevice.setHeight(signaturepad.HWGetDeviceH());
			docScanDevice.setWidth(signaturepad.HWGetDeviceW());
			docScanDevice.setDeviceType(DeviceType.SIGNATURE_PAD);
			docScanDevice.setServiceName(getServiceName());
			docScanDevice.setId("HeroFunDevice");
			docScanDevice.setName("HeroFunDevice");
			devices.add(docScanDevice);
		}

		return devices;
	}

	@Override
	public void stop(DocScanDevice docScanDevice) {
		SignaturePad signaturepad = SignaturePad.INSTANCE;
		signaturepad.HWClose();

	}

	public BufferedImage byteArrayToBufferedImage(byte[] byteArray) {
		BufferedImage bufferedImage = null;
		try {
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArray);
			bufferedImage = ImageIO.read(byteArrayInputStream);
		} catch (IOException e) {
			LOGGER.error("Failed to open serial port", e);
			return null;
		} catch (Exception e1) {
			LOGGER.error("Failed to convert into bufferedImage", e1);
			return null;
		}
		return bufferedImage;
	}

	public BufferedImage getSignatureImage(SignaturePad signaturepad) throws InterruptedException, ExecutionException, TimeoutException {
	    // Create an ExecutorService to handle the thread and return the result
	    ExecutorService executorService = Executors.newSingleThreadExecutor();

	    // Define the task to be executed in the thread
	    Callable<BufferedImage> task = () -> {
	        try {
	            int statusCode = 0;
	            int timeoutInSeconds = 60; // Set a maximum wait time (e.g., 1 minutes)
	            int elapsedSeconds = 0;

	            while (statusCode != 1 && elapsedSeconds < timeoutInSeconds) {
	                // Check the status of the signature pad
	                statusCode = signaturepad.HWIsOK();
	                if (statusCode == 0) {
	                    LOGGER.info("Signature pad not ready. Waiting...");
	                } else if (statusCode == 1) {
	                    LOGGER.info("User has signed.");
	                    break; // Exit the loop when the user has signed
	                } else {
	                    LOGGER.info("Unknown status code: " + statusCode);
	                }
	                // Wait for 1 second before checking again
	                Thread.sleep(1000);
	                elapsedSeconds++;

	    	    	System.out.println(elapsedSeconds);
	            }

	            // Handle timeout
	            if (elapsedSeconds >= timeoutInSeconds) {
	                LOGGER.error("Timeout occurred. The applicant did not complete the signature process.");
	                throw new TimeoutException("Signature process timed out.");
	            }

	            byte[] outPng = new byte[1000000]; // Assume max size of 1000000 bytes (1 MB)
	            int[] outPngLength = new int[1]; // This will hold the length of the data
	            int result = signaturepad.HWGetPng(outPng, outPngLength);
	            while (result == -3) { // If result is -3, the buffer was too small
	                LOGGER.info("Buffer too small. Increasing size...");
	                outPng = new byte[outPng.length * 2]; // Double the buffer size
	                result = signaturepad.HWGetPng(outPng, outPngLength); // Call again with a larger buffer
	            }

	            // Convert byte array to BufferedImage
	            return byteArrayToBufferedImage(outPng);

	        } catch (InterruptedException e) {
	            LOGGER.error("Failed to complete the signature process due to interruption.", e);
	            Thread.currentThread().interrupt();
	            return null;
	        } catch (Exception e) {
	            LOGGER.error("Unexpected exception while capturing signature", e);
	            return null;
	        }
	    };

	    // Submit the task to the executor service
	    Future<BufferedImage> future = executorService.submit(task);
	    
	    try {
	        BufferedImage image = future.get(60, TimeUnit.SECONDS);
	        if (image == null) {
	            LOGGER.warn("Signature image is null.");
	        }
	        return image;
	    } catch (TimeoutException e) {
	        LOGGER.error("Signature capture timed out", e);
	        future.cancel(true);
	        throw e;
	    } catch (ExecutionException e) {
	        LOGGER.error("Execution failed during signature capture", e);
	        throw e;
	    } finally {
	        try {
	            LOGGER.info("Closing signature pad...");
	            signaturepad.HWClose();
	        } catch (Exception e) {
	            LOGGER.warn("Exception while closing signature pad", e);
	        }
	        executorService.shutdown();
	    }
	}


}
