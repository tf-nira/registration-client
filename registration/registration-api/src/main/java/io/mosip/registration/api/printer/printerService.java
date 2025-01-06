package io.mosip.registration.api.printer;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;

import java.util.List;

public interface printerService {
    List<DocScanDevice> getConnectedThermal();
   List<DocScanDevice> getListedPrinter();

    String getServiceName();
}