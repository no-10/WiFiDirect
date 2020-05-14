/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.wifidirect.DeviceListFragment.DeviceActionListener;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int REQUEST_CODE_IMAGE = 111;
    protected static final int REQUEST_CODE_VIDEO = 222;
    protected static final int REQUEST_CODE_AUDIO = 333;
    protected static final int REQUEST_CODE_DOCUMENT = 444;
    private static final int RESULT_OK = 1;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
//                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                        );
                ((DeviceActionListener) getActivity()).connect(config);

            }
        });

        mContentView.findViewById(R.id.btn_begroupowner).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).beGroupOwner();
                    }
                });

        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceActionListener) getActivity()).disconnect();
                    }
                });

        mContentView.findViewById(R.id.btn_send_image).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps

                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                        startActivityForResult(intent, REQUEST_CODE_IMAGE);
                    }
                });
        mContentView.findViewById(R.id.btn_send_video).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent();
                        intent.setType("video/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                        startActivityForResult(intent, REQUEST_CODE_VIDEO);
                    }
                });
        mContentView.findViewById(R.id.btn_send_audio).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent();
                        intent.setType("audio/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                        startActivityForResult(intent, REQUEST_CODE_AUDIO);
                    }
                });
        mContentView.findViewById(R.id.btn_send_document).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent();
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                        startActivityForResult(intent, REQUEST_CODE_DOCUMENT);
                    }
                });
        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        Uri uri;
        List<String> fileList = new ArrayList<>();

        if (data != null){
            ClipData files = data.getClipData();
            if (files != null){
                for (int i=0; i<files.getItemCount(); i++){
                    Uri fileUri = files.getItemAt(i).getUri();
                    fileList.add(fileUri.toString());
                    //System.out.println(imageUri);
                }
                //uri = imageNames.getItemAt(0).getUri();
            }else {
                uri = data.getData();
                fileList.add(uri.toString());
            }
        }

        TextView statusText = (TextView) mContentView.findViewById(R.id.status_text);
        statusText.setText("Sending: " + fileList.toString());
        Log.d(WiFiDirectActivity.TAG, "Intent----------- " + fileList.toString());
        Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
        //serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                info.groupOwnerAddress.getHostAddress());
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
        serviceIntent.putExtra("REQUEST_CODE", requestCode);

        String[] fileNames = fileList.toArray(new String[]{});
        serviceIntent.putExtra("FILE_LIST",  fileNames);

        getActivity().startService(serviceIntent);
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(((info.isGroupOwner == true) ? "You can wait to receive the files."
                        : "You can send files by clicking buttons."));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setVisibility(View.GONE);
        view = (TextView) mContentView.findViewById(R.id.message);
        view.setVisibility(View.GONE);

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text))
                    .execute();
            //mContentView.findViewById(R.id.btn_begroupowner).setVisibility(View.GONE);
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView.findViewById(R.id.btn_send_image).setVisibility(View.VISIBLE);
            mContentView.findViewById(R.id.btn_send_video).setVisibility(View.VISIBLE);
            mContentView.findViewById(R.id.btn_send_audio).setVisibility(View.VISIBLE);
            mContentView.findViewById(R.id.btn_send_document).setVisibility(View.VISIBLE);
            ((TextView) mContentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_begroupowner).setVisibility(View.GONE);
        mContentView.findViewById(R.id.btn_disconnect).setVisibility(View.VISIBLE);
    }

    /**
     * Updates the UI with device data
     * 
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.message);
        view.setText("Please let Receiver click receive at first.");
        view.setVisibility(View.VISIBLE);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("You are connecting with "+device.deviceName+".");
        view.setVisibility(View.VISIBLE);


    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_begroupowner).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.btn_disconnect).setVisibility(View.GONE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_send_image).setVisibility(View.GONE);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_send_video).setVisibility(View.GONE);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_send_audio).setVisibility(View.GONE);
        view.setText(R.string.empty);
        mContentView.findViewById(R.id.btn_send_document).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private int requestCode;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                client.setSoTimeout(5000);
                Log.d(WiFiDirectActivity.TAG, "Server: connection done");

                DataInputStream dis;

                dis = new DataInputStream(client.getInputStream());
                requestCode = dis.readInt();
                int numOfFiles = dis.readInt();
                System.out.println(requestCode);
                System.out.println(numOfFiles);
                //String fileName = dis.readUTF();
                //long directory = dis.readLong();

                List<String> returnFiles = new ArrayList<>();
                Date dNow = new Date( );
                SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd-");
                String folderSuffix = ft.format(dNow)+ System.currentTimeMillis();
                String temFolder = "";
                switch (requestCode) {
                    case 111:
                        temFolder = "images/"+folderSuffix;
                        break;
                    case 222:
                        temFolder = "videos/"+folderSuffix;
                        break;
                    case 333:
                        temFolder = "audios/"+folderSuffix;
                        break;
                    default:
                        temFolder = "documents/"+folderSuffix;
                        break;
                }


                for (int i=0; i<numOfFiles; i++){

                    byte[] fileMessageByte = new byte[149];

                    int headerLength = dis.read(fileMessageByte,0,fileMessageByte.length);
                    String fileMessage = new String(fileMessageByte);

                    System.out.println(fileMessage);
                    String fileNameWithSpace = fileMessage.split("--")[1];
                    String fileName = fileNameWithSpace.split(" ")[0];
                    long fileLength = Long.parseLong(fileMessage.split("--")[2]);
                    System.out.println(fileLength);

                    System.out.println(requestCode+" "+fileName+" "+fileLength);

                    File file = new File(context.getExternalFilesDir("received/"+temFolder),
                            System.currentTimeMillis()+"-"+fileName);
                    returnFiles.add(file.getAbsolutePath());

                    System.out.println(file.getAbsolutePath());
                    FileOutputStream fos = null;
                    fos = new FileOutputStream(file);

                    Log.d(WiFiDirectActivity.TAG, "Server: accept transmission.");


                    byte[] bytes = new byte[1024];
                    int length = 0;
                    long progress = 0;

                    while(((length = dis.read(bytes, 0, bytes.length)) != -1)) {

                        fos.write(bytes, 0, length);
                        fos.flush();
                        progress += length;

                        fileLength -= length;
                        //System.out.println(fileLength+" "+length);
                        if (fileLength == 0){
                            break;
                        }
                        if (fileLength < bytes.length){
                            bytes = new byte[(int)fileLength];
                        }
                    }

                    Log.d(WiFiDirectActivity.TAG, "Server: transmission finished.");

                    if(fos != null) {
                        fos.close();
                    }
                }
                if(dis != null)
                    dis.close();
                serverSocket.close();

                String strings = "";
                for (int i=0; i<returnFiles.size();i++){
                    strings = strings+returnFiles.get(i)+"@";
                }

                return strings;
            } catch (IOException e) {
                //Log.e(WiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {

                String[] files = result.split("@");
                statusText.setText("File copied - " + files[0]);
                File recvFile = new File(files[0]);
                Uri fileUri;
                Intent intent = new Intent();

                if (Build.VERSION.SDK_INT>=24){
                    fileUri = FileProvider.getUriForFile(
                            context,
                            "com.example.android.wifidirect.fileprovider",
                            recvFile);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }else {
                    fileUri = Uri.fromFile(recvFile);
                }

                switch (requestCode) {
                    case 111:
                        intent.setDataAndType(fileUri, "image/*");
                        break;
                    case 222:
                        intent.setDataAndType(fileUri, "video/*");
                        break;
                    case 333:
                        intent.setDataAndType(fileUri, "audio/*");
                        break;
                    default:
                        intent.setDataAndType(fileUri, "*/*");
                        break;
                }
                intent.setAction(Intent.ACTION_VIEW);
                context.startActivity(intent);

            }

        }

        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

}
