package generalplus.com.blespeechplugin;

public class LogData {
	
	public static String str_log = "";
	public static String str_LcdDatalog = "";
	
	public static String str_MIDI_Index = "00";
	
	public LogData()
	{
		
	}
	
	public void SetLogText(String log)
	{
		str_log = log;		
	} 
	
	public void AddLogText(String log)
	{
		if(str_log == "")
		{
			str_log = log;
		}
		else
		{
			str_log = str_log + "\n" + log;
		}		
	}
	
	public void StroreLcdDataLog(String LcdDatalog)
	{
		if(str_LcdDatalog == "")
		{
			str_LcdDatalog = LcdDatalog;
		}
		else
		{
			str_LcdDatalog = str_LcdDatalog + "," + LcdDatalog;
		}		
	}	
	
	public String GetLogText()
	{
		return 	str_log;
	} 
	
	public void Clear()
	{
		str_log = "";
		str_LcdDatalog = "";
	}
	
	public void SetMidiIndex(String strMidiIndex)
	{
		str_MIDI_Index = strMidiIndex;
	}
	
	public String GetMidiIndex()
	{		
		return 	str_MIDI_Index;
	}

}

