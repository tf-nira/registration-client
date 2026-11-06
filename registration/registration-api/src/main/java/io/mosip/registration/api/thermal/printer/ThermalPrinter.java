package io.mosip.registration.api.thermal.printer;
import com.sun.jna.Library;
import com.sun.jna.Native;

public interface ThermalPrinter extends Library {

	ThermalPrinter INSTANCE = Native.load("POS_SDK", ThermalPrinter.class);
	
	public long POS_Port_OpenA(String a, int b, boolean c, String d);
	
	public long POS_Status_RTQueryStatus(long iPrinterID);
	
	public long POS_Output_PrintFontStringA(long iPrinterID,int iFont,int iThick,int iWidth,int iHeight,int iUnderLine,String lpString);
	
	public long POS_Output_PrintBmpDirectA(long iPrinterID, String filePath);
	
	public long POS_Control_FeedLines(long iPrinterID, long iLines);
	
	public long POS_Port_Close(long iPrinterID);
	
}
