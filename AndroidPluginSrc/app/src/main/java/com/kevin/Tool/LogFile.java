package com.kevin.Tool;

import android.os.Environment;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Regulud on 2017/9/7.
 */

public class LogFile
{
    String fileName;
    List<String> lsData = new ArrayList<String>();

    public LogFile()
    {
        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        // check folder
        String saveFolder = fullPath + File.separator + "Log";
        File Folder = new File(saveFolder);
        if (!Folder.exists())
            Folder.mkdirs();

    }

    static LogFile sLogFile = new LogFile();
    public static LogFile GetInstance()
    {
        return sLogFile;
    }

    public void SetFileName(String folderName,String shortFileName)
    {
        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        // check folder
        String saveFolder = fullPath + File.separator + folderName  ;

        File Folder = new File(saveFolder);
        if (!Folder.exists())
            Folder.mkdirs();

        this.fileName = saveFolder + File.separator + shortFileName;
    }

    public LogFile(String fileName)
    {
        this.fileName = fileName;
    }

    public LogFile(String folderName,String shortFileName)
    {
        String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        // check folder
        String saveFolder = fullPath + File.separator + folderName  ;

        File Folder = new File(saveFolder);
        if (!Folder.exists())
            Folder.mkdirs();
    }



    public void AddLog(String msg)
    {
        lsData.add(msg);
    }

    public void AddLog(Boolean bLogTime,String msg)
    {
        String str = "";
        if(bLogTime)
        {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss`SSS");
            String sTime = sdf.format(Calendar.getInstance().getTime());
            //String fileName = "BLE_Log_" + sTime;

            str += sTime + "  " ;
        }
        str += msg;
        lsData.add(str);

    }

    public void AddLogAndSave(Boolean bLogTime,String msg)
    {
        AddLog(bLogTime, msg);
        Save();
    }

    public void Save()
    {
        String data = "";
        for (int i=0;i<lsData.size();i++)
        {
            data += lsData.get(i) + "\r\n";
        }

        writeInfo(fileName,data);
    }



    /** 將資料寫入記憶卡內 */
    public void writeInfo(String fileName, String data) {
        try {

            /*
            String fullPath = Environment.getExternalStorageDirectory().getAbsolutePath();

            // check folder
            String saveFolder = fullPath + File.separator + "/BLE_Test";
            File Folder = new File(saveFolder);
            if (!Folder.exists())
                Folder.mkdirs();
*/
            // create file
            //String savePath = fullPath + File.separator + "/BLE_Test/" + fileName + ".txt";
            File file = new File(fileName);

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(data);
            bw.flush();
            bw.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
