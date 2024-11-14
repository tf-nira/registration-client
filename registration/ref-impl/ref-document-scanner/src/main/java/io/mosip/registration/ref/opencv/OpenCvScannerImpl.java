package io.mosip.registration.ref.opencv;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.DocScannerService;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import nu.pattern.OpenCV;

@Component
public class OpenCvScannerImpl implements DocScannerService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenCvScannerImpl.class);
	private static final String SERVICE_NAME = "OpenCV";
	private static final String DELIMITER = ":";


	public OpenCvScannerImpl() {
		OpenCV.loadShared();
	}

	@Override
	public String getServiceName() {
		return SERVICE_NAME;
	}

	@Override
	public BufferedImage scan(DocScanDevice docScanDevice, String deviceType) {
		LOGGER.info("Entering the opencv device impl of scan *************************************");
		int width = 640;
		int height = 480;

		int index = Integer.parseInt(docScanDevice.getName().split(DELIMITER)[1]);
		VideoCapture capture = new VideoCapture(index);
		capture.set(Videoio.CAP_PROP_BUFFERSIZE, 1);
		capture.set(Videoio.CAP_PROP_FPS, 15);

		Mat frame = new Mat(new Size(width, height), CvType.CV_8UC3);
		if (capture.isOpened()) {
			capture.read(frame);
			try {
				return mat2Img(frame);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	@Override
	public List<DocScanDevice> getConnectedDevices() {
		LOGGER.info("Entering the opencv device impl getconnected device*************************************");
		var deviceIndexList = returnCameraIndexes(0);

		List<DocScanDevice> devices = Collections.synchronizedList(new ArrayList<>());
		deviceIndexList.parallelStream().forEach(index -> {
			VideoCapture capture = new VideoCapture(index, Videoio.CAP_MSMF);
			if (capture.isOpened()) {
				DocScanDevice docScanDevice = new DocScanDevice();
				docScanDevice.setDeviceType(DeviceType.CAMERA);
				docScanDevice.setName(capture.getBackendName() + DELIMITER + index);
				docScanDevice.setServiceName(getServiceName());
				docScanDevice.setId(SERVICE_NAME + DELIMITER + capture.getBackendName());
				devices.add(docScanDevice);
				capture.release();
			}
		});
		return devices;
	}

	@Override
	public void stop(DocScanDevice docScanDevice) {
		int index = Integer.parseInt(docScanDevice.getName().split(DELIMITER)[1]);
		VideoCapture capture = new VideoCapture(index, Videoio.CAP_MSMF);
		capture.release();
	}

	public BufferedImage mat2Img(Mat mat) throws IOException {
		MatOfByte bytes = new MatOfByte();
		Imgcodecs.imencode(".jpg", mat, bytes);
		InputStream inputStream = new ByteArrayInputStream(bytes.toArray());
		return ImageIO.read(inputStream);
	}
	private List<Integer> returnCameraIndexes(int cameraIndex) {
		var cameraIndexes = new ArrayList<Integer>();
		var cap = new VideoCapture(cameraIndex, Videoio.CAP_MSMF);
		if (cap.isOpened()) {
			cameraIndexes.add(cameraIndex);
			cap.release();
		}
		return cameraIndexes;
	}
}