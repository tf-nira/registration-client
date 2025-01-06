package io.mosip.registration.api.printer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;

import io.mosip.registration.api.docscanner.DeviceType;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PrinterServiceUtil implements printerService {
    @Autowired
    public static PrinterStatusChecker printerstatuschecker;
    private static final String SERVICE_NAME ="THERMAL PRINTER" ;
    private static final String NORMAL_PRINTER = "NORMAL PRINTER";
    private static final String DEVICE_NAME = "THERMAL PRINTER";
//    @Value("${mosip.registration.ack.printer.a6.thermal}")
//    private static String thermal;


    /**
     * Retrieves all available local print services.
     *
     * @return Array of available PrintService objects.
     */
//    public static PrintService[] getAvailableLocalPrinters() {
//        return PrintServiceLookup.lookupPrintServices(DocFlavor.INPUT_STREAM.AUTOSENSE, null);
//    }

    /**
     * Checks if the specified printer is connected and ready.
     *
     * @param printer The PrintService representing the printer.
     * @return true if the printer is ready, false otherwise.
     */
//    public static boolean isPrinterReady(PrintService printer) {
//        if (printer == null) {
//            return false;
//        }
//        PrintServiceAttributeSet attributes = printer.getAttributes();
//        PrinterIsAcceptingJobs acceptingJobs = (PrinterIsAcceptingJobs) attributes.get(PrinterIsAcceptingJobs.class);
//        if (acceptingJobs != null && acceptingJobs.equals(PrinterIsAcceptingJobs.ACCEPTING_JOBS)) {
//            return true;
//        }
//        return false;
//    }

//
//    public static List<PrintService> getConnectedPrinters() {
////        PrinterStatusChecker.getPrintersWithStatus();
//
//        List<PrintService> allPrinters = new ArrayList<>();
//
//        for(PrintService printer: getAvailableLocalPrinters()) {
//
//            if (!printer.getName().contains("80mm Series Printer")) {
//                if(isPrinterReady(printer)) {
//                    allPrinters.add(printer);
//                }
//            }
//        }
//        return allPrinters;
//    }

//    public static List<PrintService> getConnectedThermalPrinters() {
//        List<PrintService> thermalPrinters = new ArrayList<>();
//        for (PrintService printer : getAvailableLocalPrinters()) {
//            if (printer.getName().contains("80mm Series Printer")) {
//                // if (printer.getName().contains(getValueFromApplicationContext(RegistrationConstants.A6_THERMAL_PRINTER))) {
////               System.out.println(thermal);
//                if(isPrinterReady(printer)) {
//                    thermalPrinters.add(printer);
//
//                }
//            }
//        }
//
//        return thermalPrinters;
//    }
    @Override
    public List<DocScanDevice> getConnectedThermal(){
        List<PrinterStatusChecker> onlyThermalPrinters =  PrinterStatusChecker.getPrintersWithStatus();
        System.out.println(onlyThermalPrinters.toString());
        if(!onlyThermalPrinters.isEmpty()) {
            DocScanDevice docScanDevice = new DocScanDevice();
            docScanDevice.setServiceName(getServiceName());
            docScanDevice.setHeight(100);
            docScanDevice.setWidth(60);
            docScanDevice.setDeviceType(DeviceType.THERMAL_PRINTER);
            docScanDevice.setName(onlyThermalPrinters.get(0).getPrinterName());
            docScanDevice.setId(onlyThermalPrinters.get(0).getPrinterName());
            docScanDevice.setStatus(onlyThermalPrinters.get(0).getStatus());
            return Arrays.asList(docScanDevice);

        }
        System.out.println("Not Listing Thermal Printer"+" "+"Creating unKnown Device Object");
        DocScanDevice unKnownDevice= new DocScanDevice();

        return null;
    }

    @Override
    public List<DocScanDevice> getListedPrinter(){
       //List<PrintService> allNormalPrinter=  getConnectedPrinters();

        List<PrinterStatusChecker> allNormalPrinter=  PrinterStatusChecker.getPrintersWithStatus();


        System.out.println(allNormalPrinter.toString());
        if(!allNormalPrinter.isEmpty()) {
            DocScanDevice docScanDevice = new DocScanDevice();
            docScanDevice.setServiceName(getServiceName());
            docScanDevice.setHeight(100);
            docScanDevice.setWidth(60);
            docScanDevice.setDeviceType(DeviceType.NORMAL_PRINTER);
            docScanDevice.setName(allNormalPrinter.get(1).getPrinterName());
            docScanDevice.setId(allNormalPrinter.get(1).getPrinterName());
            docScanDevice.setStatus(allNormalPrinter.get(1).getStatus());
            return Arrays.asList(docScanDevice);
        }
        System.out.println("Not Listing Thermal Printer"+" "+"Creating unKnown Device Object");
        DocScanDevice noPrinter= new DocScanDevice();

        return null;
    }
    @Override
    public String getServiceName(){
        return SERVICE_NAME;
    }

}