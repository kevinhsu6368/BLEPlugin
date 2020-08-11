package com.kevin.Tool;

import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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

        DeleteOldFile(Folder);
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



    public synchronized  void AddLog(String msg)
    {
        lsData.add(msg);
    }

    // 暫時關閉寫檔動作, 因為會有嚴重的 lag , 如果要寫未來需再開一條執緒去寫檔試看看
    public boolean bStopSave = true;
    public void SetStopSave(boolean bStop)
    {
        bStopSave = bStop;
    }


    public synchronized  void AddLog(Boolean bLogTime,String msg)
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

    public synchronized  void AddLogAndSave(Boolean bLogTime,String msg)
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

    public synchronized  void Save(boolean bCheckDelay)
    {
        if(bStopSave)
            return ;

        // 由於下面處理可能會太浪費時間,導致當機發生,故先拿掉下面程式碼

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

    public synchronized  void Start()
    {
        if(bRunning)
            return;

        bRunning = true;
        tf.start();
    }

    public synchronized  void Close()
    {
        bRunning = false;
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 檢查是否超過延遲寫入時間
    private boolean checkDelayWriteTimeOut()
    {
        long now = System.currentTimeMillis();
        long delta = now - preWriteTime;
        return (delta > delayWriteTime);
    }

    boolean bRunning = false;
    boolean bToWrite = false;
    private synchronized void SetWriteState(boolean bWrite)
    {
        bToWrite = bWrite;
    }
    public synchronized  boolean CanWrite()
    {
        if ( bStopSave )
            return false;

        if(lsData.size() > 0)
        {
            if (bToWrite)
                return true;
            if (checkDelayWriteTimeOut())
                return true;
        }
        return false;
    }

    public void Log2File(String msg)
    {
        Log.d(HandShake.Instance().Tag,msg);
        AddLogAndSave(true,msg);
    }

    Thread tf = new Thread(new Runnable() {
        @Override
        public void run()
        {
            while (bRunning)
            {
                try
                {
                    Thread.sleep(100);

                    if(CanWrite())
                    {
                        //SetWriteState(false);
                        SetWriteState(false);
                        // write ...
                        File file = new File(fileName);

                        boolean bWriteSystemInfo = false;
                        if (!file.exists()) {
                            file.createNewFile();
                            // write system info
                            bWriteSystemInfo = true;
                        }

                        FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
                        BufferedWriter bw = new BufferedWriter(fw);

                        if(bWriteSystemInfo)
                        {
                            bw.write(SystemInfo.ToString());
                        }

                        String sDate = GetData();
                        bw.write(sDate);
                        bw.flush();
                        bw.close();
                        fw.close();

                        ClearData();

                        Log.d("Log","Write Data = " + data);

                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    });



    private synchronized void ClearData()
    {
        lsData.clear();
    }
    private synchronized  String GetData()
    {
        String str  = "";
        for (String s : lsData)
        {
            str  += s + "\r\n";
        }
        return str;
    }

    /** 將資料寫入記憶卡內 */
    private synchronized void writeInfo(String fName, String sData)
    {
        this.fileName = fName;
        this.data = sData;
        if(bStopSave)
            return ;

        SetWriteState(true); // 開啟寫入

    }

    // log file 只保留最新的 N 個檔案
    int keepFileCount = 5;
    private void DeleteOldFile(File Folder)
    {
        // CLEAR ALL FILES
        try {
            File[] fs = Folder.listFiles();
            List<String> lsFileName = new ArrayList<String>();
            for (int i=0;i< fs.length;i++) {
                File f = fs[i];
                if(f!=null)
                    lsFileName.add(f.getAbsolutePath());
            }
            Collections.sort(lsFileName);

            int delCount = fs.length - keepFileCount;
            if (delCount < 1)
                return;

            for (int i = 0; i < delCount; i++) {
                String delFileName = lsFileName.get(i);
                for (File f : fs) {
                    String s = f.getAbsolutePath();
                    if (!s.equalsIgnoreCase(delFileName))
                        continue;

                    f.delete();
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void ReportToFTP()
    {

        int iFlag = this.fileName.lastIndexOf('/');
        String sFolder = this.fileName.substring(0,iFlag);
        File folder = new File(sFolder);
        File [] fs = folder.listFiles();

        if(fs.length>0)
        {
            FTPTools.Instance().Init("211.72.174.44","21","sdblog","sdbsdb");
            FTPTools.Instance().AddUpLoadFile(fs[fs.length - 1].getAbsolutePath());
            FTPTools.Instance().StartUpload();
        }
    }
}
