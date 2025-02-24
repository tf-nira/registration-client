package io.mosip.registration.api.impl.scanner;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.DocScannerService;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;

@Component
public class ScannerStubImpl implements DocScannerService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ScannerStubImpl.class);
	private static final String SERVICE_NAME = "MOSIP-STUB";
	private static final String DEVICE_NAME = "STUB-SCANNER";
	private static final List<String> DOC_STUB_PATHS = new ArrayList<>();
	private static int index = 0;

	static {
		DOC_STUB_PATHS.add("/images/morena_img.BMP");
		DOC_STUB_PATHS.add("/images/stubdoc.png");

	}

	@Override
	public String getServiceName() {
		return SERVICE_NAME;
	}

	@Override
	public BufferedImage scan(DocScanDevice docScanDevice, String deviceType) {
		try (InputStream inputStream = this.getClass().getResourceAsStream(getStubPath())) {
			BufferedImage bufferedImage = ImageIO.read(inputStream);

			if (docScanDevice.getFrame() != null && docScanDevice.getFrame().length > 3) {
				return bufferedImage.getSubimage(docScanDevice.getFrame()[0], docScanDevice.getFrame()[1],
						docScanDevice.getFrame()[2], docScanDevice.getFrame()[3]);
			}
			return bufferedImage;
		} catch (IOException e) {
			LOGGER.error("Failed to stub document", e);
		}
		return null;
	}

	@Override
	public List<DocScanDevice> getConnectedDevices(String enabled) {
		if ("Yes".equalsIgnoreCase(enabled)) {
			DocScanDevice docScanDevice = new DocScanDevice();
			docScanDevice.setServiceName(getServiceName());
			docScanDevice.setDeviceType(DeviceType.SCANNER);
			docScanDevice.setName(DEVICE_NAME);
			docScanDevice.setId(DEVICE_NAME);
			return Arrays.asList(docScanDevice);
		}
		return null;
	}

	@Override
	public void stop(DocScanDevice docScanDevice) {
		// Do nothing
	}

	private String getStubPath() {
		index = index % DOC_STUB_PATHS.size();
		return DOC_STUB_PATHS.get(index++);
	}
}
