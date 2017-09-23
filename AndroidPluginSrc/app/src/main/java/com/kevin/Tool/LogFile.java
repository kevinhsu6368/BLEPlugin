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
    // 延遲寫入時間
    long delayWriteTime = 10 * 1000; //  預設為 60 秒 寫入一次

    //  最近一次寫入的時間點
    long preWriteTime = 0;

    String fileName;
    String data;
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

    // 暫時關閉寫檔動作, 因為會有嚴重的 lag , 如果要寫未來需再開一條執緒去寫檔試看看
    boolean bStopSave = true;

    public void AddLog(Boolean bLogTime,String msg)
    {
        if(bStopSave)
            return ;

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
        if(bStopSave)
            return ;

        AddLog(bLogTime, msg);
        Save(true);
    }

    // 強制寫入(不受延遲寫入)
    public void FlushSave()
    {
        if(bStopSave)
            return ;

        Save(false);
    }

    public void Save(boolean bCheckDelay)
    {
        if(bStopSave)
            return ;

        // 檢查時間
        if(bCheckDelay && !checkDelayWriteTimeOut())
            return;

        String data = "";
        for (int i=0;i<lsData.size();i++)
        {
            data += lsData.get(i) + "\r\n";
        }

        preWriteTime = System.currentTimeMillis();
        writeInfo(fileName,data);

    }

    // 檢查是否超過延遲寫入時間
    private boolean checkDelayWriteTimeOut()
    {
        long now = System.currentTimeMillis();
        long delta = now - preWriteTime;
        return (delta > delayWriteTime);
    }


    /** 將資料寫入記憶卡內 */
    private void writeInfo(String fName, String sData)
    {
        this.fileName = fName;
        this.data = sData;
        if(bStopSave)
            return ;


        Thread tf = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

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
        });
        tf.start();


    }

}
