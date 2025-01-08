package io.mosip.registration.api.printer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
//@Component
public  class PrinterStatusChecker {
    private final PrintService printService;

    @ToString.Include
    private final String status;

    @ToString.Include(name = "Printer Name")
    public String getPrinterName() {
        return printService.getName();
    }


    public static List<PrinterStatusChecker> getPrintersWithStatus() {
        List<PrinterStatusChecker> printServicesWithStatus = new ArrayList<>();
        try {
            // Enhanced WMI query
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe", "/c", "wmic printer get Name,WorkOffline"
            );
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("Name")) {
                        continue;
                    }

                    // Parse the printer details
                    String[] details = line.trim().split("\\s{2,}");
                    if (details.length >= 2) {
                        String name = details[0];
                        if (name.equalsIgnoreCase("OneNote (Desktop)") || name.equalsIgnoreCase("Microsoft Print to PDF")) {
                            continue;
                        }

                        String workOffline = details[1];
                        String inferredStatus = "Unknown";
                        if (workOffline.equalsIgnoreCase("TRUE")) {
                            inferredStatus = "Offline";
                        } else if (workOffline.equalsIgnoreCase("FALSE")) {
                            inferredStatus = "Connected";
                        }

                        // Find the corresponding PrintService
                        PrintService[] availableServices = PrintServiceLookup.lookupPrintServices(null, null);
                        for (PrintService service : availableServices) {
                            if (service.getName().equalsIgnoreCase(name)  && "Connected".equalsIgnoreCase(inferredStatus)) {
                                printServicesWithStatus.add(new PrinterStatusChecker(service, inferredStatus));
                                continue;
                            }
                        }
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return printServicesWithStatus;
    }
}