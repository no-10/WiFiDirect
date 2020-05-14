// Copyright 2011 Google Inc. All Rights Reserved.

package com.example.android.wifidirect;

import android.Manifest;
import android.app.Activity;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.CursorLoader;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {



    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onHandleIntent(Intent intent) {

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            //Get all file names.
            String[] fileNames = intent.getStringArrayExtra("FILE_LIST");


            //String path = fileUri.substring(7);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            int requestCode = intent.getIntExtra("REQUEST_CODE", 0);
            Socket socket = new Socket();

            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());

                ContentResolver cr = context.getContentResolver();
                InputStream is = null;

                //Uri uri = Uri.parse(fileUri);
                File file;

                dos.writeInt(requestCode);
                //dos.flush();
                dos.writeInt(fileNames.length);
                //dos.flush();

                for (int i=0; i<fileNames.length; i++){
                    System.out.println("String[]:"+fileNames[i]);

                    String fileUri = fileNames[i];
                    int fileLength = 0;
                    try {

                        is = cr.openInputStream(Uri.parse(fileUri));
                    } catch (FileNotFoundException e) {
                        Log.d(WiFiDirectActivity.TAG, e.toString());
                    }
                    FileInputStream fis = (FileInputStream)is;

                    switch (fileUri.charAt(0)){
                        case 'c':
                            String filePath = getPathFromUri(context, Uri.parse(fileUri));

                            if (filePath != null){
                                file = new File(filePath);
                                fileLength = (int)file.length();
                                System.out.println("the filelength is "+fileLength);
                                Log.d(WiFiDirectActivity.TAG, "Client: The file path "
                                        + file.getAbsolutePath() + " is "
                                        + file.exists());

                                String fileMessage = String.format("Start--%-128s--%012d",file.getName(),file.length());
                                System.out.println(fileMessage);
                                System.out.println(fileMessage.getBytes().length);
                                System.out.println(file.length());
                                dos.write(fileMessage.getBytes(),0,fileMessage.getBytes().length);
                                dos.flush();
                                //dos.writeUTF(fileMessage);
                                //dos.flush();

                            }else {
                                Log.d(WiFiDirectActivity.TAG, "Client: Uri cannot transfer to the file path.");
                            }
                            break;
                        case 'f':
                            file = new File(fileUri.substring(7));
                            fileLength = (int)file.length();
                            System.out.println("the filelength is "+fileLength);
                            Log.d(WiFiDirectActivity.TAG, "Client: The file path "
                                    + file.getAbsolutePath() + " is "
                                    + file.exists());

                            String fileMessage = String.format("Start--%-128s--%012d",file.getName(),file.length());
                            System.out.println(fileMessage);
                            System.out.println(fileMessage.getBytes().length);
                            System.out.println(file.length());
                            dos.write(fileMessage.getBytes(),0,fileMessage.getBytes().length);
                            dos.flush();
                            //dos.writeUTF(fileMessage);
                            //dos.flush();
                            break;
                        default:
                            Log.d(WiFiDirectActivity.TAG, "Client: Cannot parse the Uri.");

                    }

                    Log.d(WiFiDirectActivity.TAG, "Client: Start transmission.");

                    byte[] bytes = new byte[1024];
                    int length = 0;
                    long progress = 0;

                    while((length = fis.read(bytes, 0, bytes.length)) != -1) {
                        if (length!=1024) System.out.println(length);
                        dos.write(bytes, 0, length);
                        dos.flush();
                        progress += length;
                    }

                    //String endFlag =  String.format("%-3s","end");
                    //System.out.println("the length of endflag is "+endFlag.getBytes().length);
                    //dos.write(endFlag.getBytes(), 0, endFlag.getBytes().length);
                    //dos.flush();
                    //progress += endFlag.getBytes().length;

                    System.out.println(progress);

                    Log.d(WiFiDirectActivity.TAG, "Client: Data written");

                    if(fis != null)
                        fis.close();
                }

                if(dos != null)
                    dos.close();

            } catch (IOException e) {
                Log.e(WiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static String getPathFromUri(Context context, Uri uri) {
        String path = null;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            //如果是document类型的Uri，通过document id处理，内部会调用Uri.decode(docId)进行解码
            String docId = DocumentsContract.getDocumentId(uri);
            //primary:Azbtrace.txt
            //video:A1283522
            String[] splits = docId.split(":");
            String type = null, id = null;
            if(splits.length == 2) {
                type = splits[0];
                id = splits[1];
            }
            switch (uri.getAuthority()) {
                case "com.android.externalstorage.documents":
                    if("primary".equals(type)) {
                        path = Environment.getExternalStorageDirectory() + File.separator + id;
                    }
                    break;
                case "com.android.providers.downloads.documents":
                    if("raw".equals(type)) {
                        path = id;
                    } else {
                        Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(docId));
                        path = getMediaPathFromUri(context, contentUri, null, null);
                    }
                    break;
                case "com.android.providers.media.documents":
                    Uri externalUri = null;
                    switch (type) {
                        case "image":
                            externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            externalUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }
                    if(externalUri != null) {
                        String selection = "_id=?";
                        String[] selectionArgs = new String[]{ id };
                        path = getMediaPathFromUri(context, externalUri, selection, selectionArgs);
                    }
                    break;
            }
        } else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            path = getMediaPathFromUri(context, uri, null, null);
        } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
            //如果是file类型的Uri(uri.fromFile)，直接获取图片路径即可
            path = uri.getPath();
        }
        //确保如果返回路径，则路径合法
        return path == null ? null : (new File(path).exists() ? path : null);
    }

    private static String getMediaPathFromUri(Context context, Uri uri, String selection, String[] selectionArgs) {
        String path;
        String authroity = uri.getAuthority();
        path = uri.getPath();
        String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        if(!path.startsWith(sdPath)) {
            int sepIndex = path.indexOf(File.separator, 1);
            if(sepIndex == -1) path = null;
            else {
                path = sdPath + path.substring(sepIndex);
            }
        }

        if(path == null || !new File(path).exists()) {
            ContentResolver resolver = context.getContentResolver();
            String[] projection = new String[]{ MediaStore.MediaColumns.DATA };
            Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    try {
                        int index = cursor.getColumnIndexOrThrow(projection[0]);
                        if (index != -1) path = cursor.getString(index);
                        Log.i(WiFiDirectActivity.TAG, "getMediaPathFromUri query " + path);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        path = null;
                    } finally {
                        cursor.close();
                    }
                }
            }
        }
        return path;
    }

}
