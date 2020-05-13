package com.dji.mopdemo;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.mop.common.Pipeline;
import dji.mop.common.PipelineError;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.payload.Payload;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class PipelineAdapter extends RecyclerView.Adapter<PipelineAdapter.ViewHolder> {
    private List<Pipeline> data;
    private LayoutInflater mInflater;
    private OnDisconnectListener listener = new OnDisconnectListener() {
        @Override
        public void onDisconnect(Pipeline d) {
            if (data.contains(d)) {
                data.remove(d);
                notifyItemRemoved(data.indexOf(d));
            }

        }
    };


    public PipelineAdapter(Context context, List<Pipeline> data) {
        this.mInflater = LayoutInflater.from(context);
        this.data = data;
    }

    public void addItem(Pipeline action) {
        if (action == null || data == null || data.contains(action)) {
            return;
        }
        data.add(action);
        notifyItemInserted(getItemCount() - 1);
    }

    public List<Pipeline> getData() {
        return data;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(mInflater.inflate(R.layout.item_pipeline, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(data.get(position));
        holder.setListener(listener);
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        for (int i = 0; i < getItemCount(); i++) {
            ViewHolder viewholder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (viewholder != null) {
                viewholder.destroy();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.destroy();
    }

    public static String getTime() {
        String patten = "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat format = new SimpleDateFormat(patten);
        String dateFormatStr = format.format(new Date());
        return dateFormatStr;
    }

    //    @Override
    public void onDisconnect(Pipeline data) {
        this.data.remove(data);
        notifyItemRemoved(this.data.indexOf(data));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nameTv;
        private TextView downloadTv;
        private TextView uploadTv;
        private TextView downloadLogTv;
        private TextView uploadLogTv;
        private TextView disconnectTv;
        private TextView filenameTv;
        private Switch autoDownloadSwitch;

        HandlerThread uploadThread;
        HandlerThread downloadThread;
        Handler uploadHandler;
        Handler downloadHandler;
        private Payload payload;
        private FlightController flightController;
        private WeakReference<OnDisconnectListener> listenerWeakReference;

        private boolean uploading;
        private boolean downloading;

        private String uploadFileInfoLog;
        private String uploadProgress;
        private String uploadResult;

        private String downloadFileInfoLog;
        private String downloadProgress;
        private String downloadResult;
        private int downloadPackCount;
        private int downloadSize;

        private int downloadSuccessCount;
        private int downloadCount;

        private int uploadSuccessCount;
        private int uploadCount;

        private String uploadFileName = "mop.log";
        private OnEventListener listener = new OnEventListener() {
            @Override
            public void onTipEvent(MOPCmdHelper.TipEvent event) {
                handlerTipEvent(event);
            }

            @Override
            public void onFileInfoEvent(MOPCmdHelper.FileInfo event) {
                onEvent3BackgroundThread(event);
            }
        };

        public ViewHolder(View itemView) {
            super(itemView);
            nameTv = itemView.findViewById(R.id.tv_name);
            downloadTv = itemView.findViewById(R.id.tv_download);
            uploadTv = itemView.findViewById(R.id.tv_upload);
            downloadLogTv = itemView.findViewById(R.id.tv_download_log);
            uploadLogTv = itemView.findViewById(R.id.tv_upload_log);
            disconnectTv = itemView.findViewById(R.id.tv_disconnect);
            filenameTv = itemView.findViewById(R.id.et_file_name);
            autoDownloadSwitch = itemView.findViewById(R.id.switch_auto_download);

            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null && product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
                payload = product.getPayload();
            }
        }

        public void setData(Pipeline data) {
            destroy();
            uploadThread = new HandlerThread("upload");
            downloadThread = new HandlerThread("download");
            uploadThread.start();
            downloadThread.start();
            uploadHandler = new Handler(uploadThread.getLooper());
            downloadHandler = new Handler(downloadThread.getLooper());
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (v.getId()) {
                        case R.id.tv_upload:
                            View root = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_mop_upload, null, false);
                            new AlertDialog.Builder(itemView.getContext())
                                    .setTitle("Select File Size")
                                    .setView(root)
                                    .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            RadioGroup group = root.findViewById(R.id.group_file);
                                            switch (group.getCheckedRadioButtonId()) {
                                                case R.id.rb_1:
                                                    uploadFileName = "mop.log";
                                                    break;
                                                case R.id.rb_2:
                                                    uploadFileName = "mop.jpeg";
                                                    break;
                                                case R.id.rb_3:
                                                    uploadFileName = "mop.mp4";
                                                    break;
                                            }
                                            uploadHandler.post(() -> {
                                                resetUploadInfo();
                                                uploadFile(data);
                                            });
                                        }
                                    })
                                    .show();
                            break;
                        case R.id.tv_download:
                            downloadHandler.post(() -> {
                                resetDownInfo();
                                downloadFile(data, filenameTv.getText().toString());
                            });
                            break;
                        case R.id.tv_disconnect:
                            destroy();
                            disconnect(data);
                            break;
                    }
                }
            };

            String title = String.format("Id=%d, MOPType = %s, trans_type=%s", data.getId(), data.getDeviceType(), data.getType());
            nameTv.setText(title);
            downloadTv.setOnClickListener(listener);
            uploadTv.setOnClickListener(listener);
            disconnectTv.setOnClickListener(listener);
        }

        public void setListener(OnDisconnectListener listener) {
            listenerWeakReference = new WeakReference<OnDisconnectListener>(listener);
        }

        private void disconnect(Pipeline data) {
            switch (data.getDeviceType()) {
                case PAYLOAD:
                    if (payload == null) {
                        return;
                    }
                    payload.getPipelines().disconnect(data.getId(), new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError error) {
                            if (error == null) {
                                toast("disconnect success");
                            } else {
                                toast("disconnect fail:" + error.toString());
                            }
                        }
                    });
                    break;
                case ON_BOARD:
                    if (flightController == null) {
                        return;
                    }
                    flightController.getPipelines().disconnect(data.getId(), new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError error) {
                            if (error == null) {
                                toast("disconnect success");
                            } else {
                                toast("disconnect fail:" + error.toString());
                            }
                        }
                    });
                    break;
            }
            if (data != null && listenerWeakReference != null && listenerWeakReference.get() != null) {
                listenerWeakReference.get().onDisconnect(data);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void destroy() {
            if (uploadHandler != null && uploadThread != null) {
                uploadHandler.removeCallbacksAndMessages(null);
                uploadThread.quitSafely();
            }
            if (downloadHandler != null && downloadThread != null) {
                uploadHandler.removeCallbacksAndMessages(null);
                downloadThread.quitSafely();
            }
            resetDownInfo();
            resetUploadInfo();
        }

        private void resetDownInfo() {
            downloadPackCount = 0;
            downloadSize = 0;

            downloadFileInfoLog = "";
            downloadProgress = "";
            downloadResult = "";
            downloadPackCount = 0;
            downloadSize = 0;
        }

        private void resetUploadInfo() {
            uploadFileInfoLog = "";
            uploadProgress = "";
            uploadResult = "";
        }

        private void uploadFile(Pipeline data) {
            if (data == null) {
                return;
            }
            if (uploading) {
                toast("uploading");
                return;
            }
            uploading = true;
            long time = System.currentTimeMillis();

            InputStream inputStream = null;
            FileOutputStream out = null;
            ByteArrayOutputStream outputStream = null;
            try {
                byte[] buff = new byte[3072];
                inputStream = itemView.getContext().getAssets().open("mop/" + uploadFileName);
                Log.e("hooyee_mop", inputStream.toString());
                File tmp = new File(itemView.getContext().getCacheDir(),  "mop.tmp");
                out = new FileOutputStream(tmp);
                outputStream = new ByteArrayOutputStream();
                int len;
                while ((len = inputStream.read(buff, 0, 3072)) > 0) {
                    outputStream.write(buff, 0, len);
                    out.write(buff, 0, len);
                }
                MOPCmdHelper.sendUploadFileReq(data, uploadFileName, outputStream.toByteArray(), time, MOPCmdHelper.getMD5(tmp), listener);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                    out.close();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            uploadCount++;
            updateUploadUI();
            uploading = false;
//            if (autoDownloadSwitch.isChecked()) {
//                uploadTv.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        uploadTv.performClick();
//                    }
//                }, 500);
//            }
        }

        private void downloadFile(Pipeline pipeline, String filename) {
            if (pipeline == null) {
                return;
            }

            if (downloading) {
                toast("downloading");
                return;
            }
            downloading = true;
            long time = System.currentTimeMillis();

            // 获取文件信息
            MOPCmdHelper.FileInfo fileInfo = MOPCmdHelper.sendDownloadFileReq(pipeline, filename, listener);
            if (fileInfo == null || !fileInfo.isExist) {
                DJILog.logWriteE("PipelineAdapter", "downloadFile fail", "/MOP");
                downloading = false;
                return;
            }
            downloadFileInfoLog = fileInfo.toString();
            updateDownloadUI();

            RandomAccessFile stream = null;
            try {
                File file = new File(getExternalCacheDirPath(itemView.getContext(), fileInfo.filename));
                if (file.exists()) {
                    file.delete();
                    file.createNewFile();
                } else {
                    file.createNewFile();
                }
                stream = new RandomAccessFile(file, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (true) {
                // 开始读取文件数据
                byte[] headBuff = new byte[MOPCmdHelper.PACK_HEADER_SIZE];
                int len1 = pipeline.readData(headBuff, 0, headBuff.length);
                if (len1 < MOPCmdHelper.PACK_HEADER_SIZE) {
                    // TODO
                    continue;
                }
                // 这个包带有的文件字节
                MOPCmdHelper.FileTransResult result = MOPCmdHelper.parseFileDataCmd(headBuff);
                if (result == null) {
                    downloading = false;
                    return;
                }
                if (result.success) {
                    int length = result.length;
                    byte[] dataBuff = new byte[length];

                    int sum = 0;
                    int readLength = length;
                    while (sum < length) {
                        int len = pipeline.readData(dataBuff, 0, readLength);

                        if (len > 0) {
                            downloadPackCount ++;
                            downloadSize += len;
                            DJILog.logWriteI("PipelineAdapter", filename + " download : " + sum);
                            byte[] subArray = ByteUtils.subArray(dataBuff, 0, 3);
                            String tmp = ByteUtils.toHexString(subArray);
                            Log.e("hooyee_pipe_download", "pack seq:" + downloadPackCount + ", data:" + tmp + ", length:" + len);
                            sum += len;
                            readLength -= len;
                            downloadLogTv.post(new Runnable() {
                                @Override
                                public void run() {
                                    downloadProgress = String.format("have downloadPack = %d, downloadSize:%d/%d, useTime:%d(ms)", downloadPackCount, downloadSize, fileInfo.fileLength, System.currentTimeMillis() - time);
                                    updateDownloadUI();
                                }
                            });
                            try {
                                stream.write(dataBuff, 0, len);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    // 解析出错位置
                    int position = MOPCmdHelper.parseFileFailIndex(pipeline);
                    // ack
                    MOPCmdHelper.sendTransFileFailAck(pipeline, position);
                    // 确认是否能接着传
                    if (MOPCmdHelper.parseCommonAck(pipeline)) {
                        try {
                            stream.seek(position);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        downloadLogTv.post(new Runnable() {
                            @Override
                            public void run() {
                                downloadLogTv.setText("transfer failure");
                            }
                        });
                        downloading = false;
                        return;
                    }

                }
                if (MOPCmdHelper.isFileEnd(headBuff)) {
                    downloadResult = "Download success";
                    updateDownloadUI();
                    break;
                }
            }
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            downloading = false;

            boolean result = verifyMd5(fileInfo, new File(getExternalCacheDirPath(itemView.getContext(), fileInfo.filename)));
            downloadResult = "verify md5 :" + result;
            downloadCount++;
            if (downloadSize == fileInfo.fileLength && result) {
                downloadSuccessCount++;
                MOPCmdHelper.sendTransAck(pipeline, true);
            } else {
                MOPCmdHelper.sendTransAck(pipeline, false);
            }
            updateDownloadUI();
            if (autoDownloadSwitch.isChecked()) {
                downloadTv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        downloadTv.performClick();
                    }
                }, 500);
            }
        }

        private boolean verifyMd5(MOPCmdHelper.FileInfo fileInfo, File file) {
            String md5 = ByteUtils.toHexString(fileInfo.md5);
            String md5_1 = ByteUtils.toHexString(MOPCmdHelper.getMD5(file));
            String log = String.format("MOPCmdHelper.FileInfo_md5: %s, file_md5:%s", md5, md5_1);
            DJILog.logWriteE("PipelineAdapter", log, "/MOP");
            return md5.equals(md5_1);
        }

        private void toast(String text) {
            if (itemView == null) {
                return;
            }
            itemView.post(() -> Toast.makeText(itemView.getContext(), text, Toast.LENGTH_SHORT).show());
        }

        public void handlerTipEvent(MOPCmdHelper.TipEvent event) {
            switch (event.type) {
                case MOPCmdHelper.TipEvent.UPLOAD:
                    if (event.result != null) {
                        uploadResult = event.result;
                        if (uploadResult.contains("Success")) {
                            // hard code
                            uploadSuccessCount++;
                        }
                    }
                    if (event.progress != null) {
                        uploadProgress = event.progress;
                    }
                    updateUploadUI();
                    break;
                case MOPCmdHelper.TipEvent.DOWNLOAD:
                    if (event.result != null) {
                        downloadResult = event.result;
                    }
                    if (event.progress != null) {
                        downloadProgress = event.progress;
                    }
                    downloadLogTv.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadLogTv.setText(downloadLogTv.getText().toString() + "\n" + event.result);
                        }
                    });
                    break;
            }

        }

        public void onEvent3BackgroundThread(MOPCmdHelper.FileInfo event) {
            uploadFileInfoLog = event.toString();
            updateUploadUI();
        }

        private void updateUploadUI() {
            StringBuffer sb = new StringBuffer("Upload:" + "\n")
                    .append(uploadFileInfoLog == null ? "" : uploadFileInfoLog).append("\n")
                    .append(uploadProgress == null ? "" : uploadProgress).append("\n")
                    .append(uploadResult == null ? "" : uploadResult).append("\n")
                    .append("成功/次数：" + uploadSuccessCount + "/" + uploadCount);

            uploadLogTv.post(new Runnable() {
                @Override
                public void run() {
                    uploadLogTv.setText(sb.toString());
                }
            });
        }

        private void updateDownloadUI() {
            StringBuffer sb = new StringBuffer("Download:" + "\n")
                    .append(downloadFileInfoLog == null ? "" : downloadFileInfoLog).append("\n")
                    .append(downloadProgress == null ? "" : downloadProgress).append("\n")
                    .append(downloadResult == null ? "" : downloadResult).append("\n")
                    .append("成功/次数：" + downloadSuccessCount + "/" + downloadCount);
            downloadLogTv.post(new Runnable() {
                @Override
                public void run() {
                    downloadLogTv.setText(sb.toString());
                }
            });
        }

    }

    public static String getExternalCacheDirPath(Context context,String path) {
        String dirName = Environment.getExternalStorageDirectory() + "/DJI/";
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            dirName = context.getExternalFilesDir("DJI").getPath() + File.separator;
        }
        return dirName + context.getPackageName() + File.separator + path;
    }

    public interface OnDisconnectListener {
        void onDisconnect(Pipeline data);
    }

    public interface OnEventListener {
        void onTipEvent(MOPCmdHelper.TipEvent event);

        void onFileInfoEvent(MOPCmdHelper.FileInfo event);
    }
}
