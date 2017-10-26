package com.kevin.Tool;


import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.*;


/**
 * Created by Kevin Hsu  on 2017/10/20.
 */

public class FTPTools
{
    private FTPTools()
    {

    }

    static FTPTools mgr = new FTPTools();

    public static FTPTools Instance()
    {
        return mgr;
    }

    public void Init(String ip,String port,String userName,String passWord)
    {
        this.ip = ip;
        this.port = port;
        this.userName = userName;
        this.passWord = passWord;
    }

    public void ChnageFolder(String folder)
    {
        sChnageFolder = folder;
    }

    private FTPClient ftpClient;
    private String ip = "127.0.0.1";
    private String port = "23";
    private String userName = "guest";
    private String passWord = "123";
    private String sChnageFolder = "";

    List<String> lsUpFiles = new ArrayList<String>();

    public void AddUpLoadFile(String fileName)
    {
        lsUpFiles.add(fileName);
    }

    Thread t_upload = new Thread(new Runnable() {
        @Override
        public void run()
        {

            ftpClient = new FTPClient();

            try
            {
                // connect and login
                ftpClient.connect(ip,Integer.parseInt(port));
                boolean bOK = ftpClient.login(userName,passWord);
                if(!bOK)
                {
                    Log.d("FTP","login   error  !!! ");
                    return;
                }

                try {
                    ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                ftpClient.enterLocalPassiveMode();

                // change folder
                if(sChnageFolder.length() > 0)
                {
                    bOK = ftpClient.changeWorkingDirectory(sChnageFolder);
                }


                // upload files
                for (String s : lsUpFiles)
                {
                    Log.d("FTP","Now will to upload file : " + s );

                    BufferedInputStream isf = new BufferedInputStream(
                            new FileInputStream(s));

                    File f = new File(s);
                    String fName = f.getName();
                    boolean replyCode = ftpClient.storeFile(fName,isf);

                    isf.close();

                    Log.d("FTP","ReplyCode =  " + replyCode);
                    Log.d("FTP","Upload finished of file : " + s );
                }


                ftpClient.disconnect();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        }
    });

    public boolean StartUpload()
    {
        t_upload.start();
        return true;
    }

    public boolean StartDownload()
    {
        return true;
    }







}
