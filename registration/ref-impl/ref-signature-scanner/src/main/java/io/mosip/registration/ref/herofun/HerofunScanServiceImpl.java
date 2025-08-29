package io.mosip.registration.ref.herofun;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
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
		LOGGER.info("Entered into Scan method....");

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
	    LOGGER.info("Signature Processing Started..."+signaturepad );

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
	                    LOGGER.info("User has signed within " + elapsedSeconds + " seconds.");
	                    break;
	                } else {
	                    LOGGER.warn("Unknown status code from pad: {}", statusCode);
	                }
	                // Wait for 1 second before checking again
	                Thread.sleep(1000);
	                elapsedSeconds++;
	                LOGGER.info("Signature Capturing "+elapsedSeconds);
	            }

	            // Handle timeout
	            if (elapsedSeconds >= timeoutInSeconds) {
	                LOGGER.error("Timeout occurred. The applicant did not complete the signature process.");
	                throw new TimeoutException("Signature process timed out.");
	            }

	            int bufferSize = 1000000;
	            byte[] outPng = new byte[bufferSize];
	            int[] outPngLength = new int[1];

	            int result = signaturepad.HWGetPng(outPng, outPngLength);
	            while (result == -3) {
	                LOGGER.info("Buffer too small. Increasing size...");
	                bufferSize *= 2;
	                outPng = new byte[bufferSize];
	                result = signaturepad.HWGetPng(outPng, outPngLength);
	            }

	            if (result != 1) {
	                LOGGER.error("Failed to get PNG from signature pad. Error code: {}", result);
	                return null;
	            }

	            // Trim array to actual size
	            byte[] trimmedPng = new byte[outPngLength[0]];
	            System.arraycopy(outPng, 0, trimmedPng, 0, outPngLength[0]);

	            return byteArrayToBufferedImage(trimmedPng);

	        } catch (InterruptedException e) {
	            LOGGER.error("Signature capture was interrupted", e);
	            Thread.currentThread().interrupt();
	            return null;
	        } catch (Exception ex) {
	            LOGGER.info("Unexpected exception during signature capture", ex);
	            return null;
	        }
	    };

	    // Submit the task to the executor service
	    Future<BufferedImage> future = executorService.submit(task);
	    
	    try {
	        return future.get(60, TimeUnit.SECONDS); // Slightly higher than capture timeout
	    } catch (TimeoutException e) {
	        LOGGER.error("Signature capture timed out", e);
	        future.cancel(true);
	        throw e;
	    } catch (ExecutionException ex) {
	        LOGGER.info("Execution failed during signature capture", ex);
	        throw ex;
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
