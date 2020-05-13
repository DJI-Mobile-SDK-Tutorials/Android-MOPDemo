package com.dji.mopdemo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import dji.log.DJILog;
import dji.mop.common.Pipeline;

/**
 * @link https://us002.fly-access.com/pages/viewpage.action?pageId=39064620
 */
public class MOPCmdHelper {
    private static final byte CMD_REQ = 0x50;
    private static final byte CMD_ACK = 0x51;
    private static final byte CMD_TRANS_ACK = 0x52;
    private static final byte CMD_FILE_INFO = 0x60;
    private static final byte CMD_DOWNLOAD = 0x61;
    private static final byte CMD_FILE_DATA = 0x62;
    private static final byte CMD_FILE_TRANS_FAIL = 0x63;
    private static final byte CMD_FILE_TRANS_FAIL_ACK = 0x64;
    private static final byte CMD_0 = 0x00;
    private static final byte CMD_1 = 0x01;
    public static final int PACK_HEADER_SIZE = 8;
    public static final int PACK_FILE_INFO_SIZE = 53;

    private static final int FILE_NAME_LENGTH = 32;

    private static final String TAG = MOPCmdHelper.class.getSimpleName();
    public static PipelineAdapter.OnEventListener listener;

    public static byte[] getUploadFileHeader() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_REQ;
        cmd[1] = CMD_0;
        return cmd;
    }

    public static byte[] getDownloadFileHeader() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_REQ;
        cmd[1] = CMD_1;
        return cmd;
    }

    public static byte[] getDownloadFile() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_DOWNLOAD;
        cmd[1] = (byte) 0xFF;
        cmd[4] = (byte) 0x20;
        return cmd;
    }

    public static byte[] getFileDataHeader(int size, int flag) {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_FILE_DATA;
        cmd[1] = (byte) flag;
        cmd[4] = (byte) (size >> 0 & 0xff);
        cmd[5] = (byte) (size >> 8 & 0xff);
        cmd[6] = (byte) (size >> 16 & 0xff);
        cmd[7] = (byte) (size >> 24 & 0xff);
        return cmd;
    }

    public static boolean sendDownloadCmd(Pipeline p) {
        byte[] cmd = getDownloadFileHeader();
        int result = p.writeData(cmd, 0, cmd.length);
        if (result > 0) {
            return parseCommonAck(p);
        }
        return false;
    }

    public static MOPCmdHelper.FileInfo sendDownloadFileReq(Pipeline p, String filename, PipelineAdapter.OnEventListener listener) {
        if (!sendDownloadCmd(p)) {
            return null;
        }
        byte[] header = getDownloadFile();
        byte[] chars = filename.getBytes();

        byte[] req = new byte[header.length + FILE_NAME_LENGTH];
        System.arraycopy(header, 0, req, 0, header.length);
        System.arraycopy(chars, 0, req, header.length, chars.length);

        if (chars.length < FILE_NAME_LENGTH) {
            req[header.length + chars.length] = '\0';
        }
        // 发送请求,要下载的文件
        int result = p.writeData(req, 0, req.length);
        if (result > 0) {

            DJILog.logWriteE(TAG, "sendDownloadFileReq ack: Success", "/MOP");
            // 读取文件信息
            byte[] fileInfoBuff = new byte[MOPCmdHelper.PACK_HEADER_SIZE + MOPCmdHelper.PACK_FILE_INFO_SIZE];
            int sum = 0;
            int readLength = fileInfoBuff.length;
            while (sum < fileInfoBuff.length) {
                // 能获取MD5等信息
                int len = p.readData(fileInfoBuff, 0, readLength);

                if (len > 0) {
                    // 由于read不支持offset，这里处理fileInfoBuff的拼接，应该使用临时的byte[]来copyArray
                    sum += len;
                    readLength -= len;
                    DJILog.logWriteE(TAG, "sendDownloadFileReq download:" + sum, "/MOP");
                } else {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            MOPCmdHelper.FileInfo fileInfo = MOPCmdHelper.FileInfo.parse(fileInfoBuff);
            return fileInfo;

        } else {
            postResultTipEvent(TipEvent.DOWNLOAD, "request failure", null, listener);
        }

        return null;
    }

    public static int sendTransFileFailReq(Pipeline pipeline, long length) {
        byte[] buff = new byte[12];
        buff[0] = CMD_FILE_TRANS_FAIL;
        buff[1] = (byte) 0xFF;
        buff[7] = 0x04;
        buff[8] = (byte) (length >> 0 & 0xff);
        buff[9] = (byte) (length >> 8 & 0xff);
        buff[10] = (byte) (length >> 16 & 0xff);
        buff[11] = (byte) (length >> 24 & 0xff);

        int len = getInt(buff, PACK_HEADER_SIZE, 4);
        int result = pipeline.writeData(buff, 0, buff.length);
        DJILog.logWriteI(TAG, "sendTransFileFailReq:" + result, "/MOP");
        DJILog.logWriteI(TAG, "len:" + len, "/MOP");
        return result;
    }

    public static int sendTransFileFailAck(Pipeline pipeline, long length) {
        byte[] buff = new byte[12];
        buff[0] = CMD_FILE_TRANS_FAIL_ACK;
        buff[1] = (byte) 0xFF;
        buff[7] = 0x04;
        buff[8] = (byte) (length >> 0 & 0xff);
        buff[9] = (byte) (length >> 8 & 0xff);
        buff[10] = (byte) (length >> 16 & 0xff);
        buff[11] = (byte) (length >> 24 & 0xff);
        int result = pipeline.writeData(buff, 0, buff.length);
        DJILog.logWriteI(TAG, "sendTransFileFailAck:" + result, "/MOP");
        return result;
    }

    public static int parseTransFileFailAck(Pipeline p) {
        byte[] buff = new byte[12];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "parseTransFileFailAck wait ack: " + size, "/MOP");
        }
        int length = getInt(buff, PACK_HEADER_SIZE, 4);
        return length;
    }


    public static boolean parseCommonAck(Pipeline p) {
        // 读取文件下载的信息
        byte[] buff = new byte[PACK_HEADER_SIZE];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "sendDownloadFileReq wait ack: " + size, "/MOP");
        }
        return (buff[0] == CMD_ACK) && (buff[1] == CMD_0);
    }

    public static boolean parseUploadAck(Pipeline p) {
        // 读取文件下载的信息
        byte[] buff = new byte[PACK_HEADER_SIZE];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "upload ack: " + size, "/MOP");
        }
        return (buff[0] == CMD_TRANS_ACK) && (buff[1] == CMD_0);
    }

    public static FileTransResult parseFileDataCmd(byte[] buff) {
        if (buff[0] == CMD_FILE_DATA) {
            int length = getInt(buff, 4, 4);
            return new FileTransResult(true, length);
        }
        if (buff[0] == CMD_FILE_TRANS_FAIL) {
            int length = getInt(buff, 4, 4);
            return new FileTransResult(false, length);
        }
        DJILog.logWriteE(TAG, "parseFileDataCmd error", "/MOP");
        return null;
    }

    public static int sendTransAck(Pipeline p, boolean result) {
        byte[] buff = new byte[PACK_HEADER_SIZE];
        buff[0] = CMD_TRANS_ACK;
        buff[1] = result ? CMD_0 : CMD_1;
        return p.writeData(buff, 0, buff.length);
    }

    public static int sendAck(Pipeline p, byte cmd) {
        byte[] buff = new byte[PACK_HEADER_SIZE];
        buff[0] = CMD_ACK;
        buff[1] = cmd;
        return p.writeData(buff, 0, buff.length);
    }

    public static boolean isFileEnd(byte[] buff) {
        return buff[0] == CMD_FILE_DATA && buff[1] == CMD_1;
    }

    public static int sendUploadFileReq(Pipeline data, String filename, byte[] buff, long time, byte[] md5, PipelineAdapter.OnEventListener listener) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.filename = filename;
        fileInfo.fileLength = buff.length;
        fileInfo.md5 = md5;
        DJILog.logWriteE(TAG, "sendUploadFileReq fileInfo:" + fileInfo, "/MOP");
        listener.onFileInfoEvent(fileInfo);

        byte[] header = getUploadFileHeader();
        int result = data.writeData(getUploadFileHeader(), 0, header.length);
        if (result < 0) {
            postResultTipEvent(TipEvent.UPLOAD, "Upload Failure: " + result, null, listener);
            return result;
        }
        if (parseCommonAck(data)) {
            // 发送md5等
            byte[] fileHealder = fileInfo.getHeader();
            result = data.writeData(fileHealder, 0, fileHealder.length);
            DJILog.logWriteE(TAG, "sendUploadFileReq send md5:" + result, "/MOP");
            if (result > 0) {
                if (parseCommonAck(data)) {
                    // 上传文件
                    uploadFile(data, buff, time, listener);
                    // 上传完的ack
                    if (parseUploadAck(data)) {
                        postResultTipEvent(TipEvent.UPLOAD, "Upload Success", null, listener);
                    }
                } else {
                    postResultTipEvent(TipEvent.UPLOAD, "Upload Failure", null, listener);
                }
            }
        }
        return result;
    }

    private static void postResultTipEvent(int type, String result, String progress, PipelineAdapter.OnEventListener listener) {
        TipEvent event = new TipEvent(type);
        event.result = result;
        event.progress = progress;
        listener.onTipEvent(event);
    }

    private static int uploadFile(Pipeline data, byte[] buff, long time, PipelineAdapter.OnEventListener listener) {
        int size = 3072;
        byte[] header;
        int hadWrote = 0;
        if (buff.length <= size) {
            header = getFileDataHeader(buff.length, CMD_1);
            int result = writeData(data, buff, time, header, hadWrote, buff.length, listener);
            DJILog.logWriteE(TAG, "uploadFile: " + result, "/MOP");
            return result;
        } else {
            while (buff.length - hadWrote > size) {
                header = getFileDataHeader(size, CMD_0);
                int result = writeData(data, buff, time, header, hadWrote, size, listener);

                hadWrote += size;
                DJILog.logWriteE(TAG, "uploadFile: " + hadWrote + " result:" + result);
            }
            int length = buff.length - hadWrote;
            header = getFileDataHeader(length, CMD_1);
            int result = writeData(data, buff, time, header, hadWrote, length, listener);
            hadWrote += result;
            DJILog.logWriteE(TAG, "uploadFile: " + length, "/MOP");
        }

        String progress = String.format("uploadSize:%d, useTime:%d(ms)", hadWrote, System.currentTimeMillis() - time);
        postResultTipEvent(TipEvent.UPLOAD, null, progress, listener);
        return 0;
    }

    private static int writeData(Pipeline pipeline, byte[] buff, long time, byte[] header, int hadWrote, int length, PipelineAdapter.OnEventListener listener) {
        byte[] d = new byte[length + header.length];
        System.arraycopy(header, 0, d, 0, header.length);
        System.arraycopy(buff, hadWrote, d, header.length, length);
        int result = pipeline.writeData(d, 0, d.length);

        String progress = String.format("uploadSize:%d, useTime:%d(ms)", hadWrote, System.currentTimeMillis() - time);
        postResultTipEvent(TipEvent.UPLOAD, null, progress, listener);

        if (result < 0) {
            DJILog.logWriteE(TAG, "writeData miss: " + result);
            // 发送上传出错的req
            sendTransFileFailReq(pipeline, hadWrote);
            // 读取对端返回的已读长度
            int len = parseTransFileFailAck(pipeline);
            if (len == hadWrote) {
                sendAck(pipeline, CMD_0);
                return writeData(pipeline, buff, time, header, hadWrote, length, listener);
            } else {
                DJILog.logWriteE(TAG, "writeData error: " + len);
                sendAck(pipeline, CMD_1);
            }
        }
        return result;
    }

    public static int parseFileFailIndex(Pipeline pipeline) {
        byte[] buff = new byte[4];
        pipeline.readData(buff, 0, 4);
        int length = getInt(buff, 0, 4);
        return length;
    }


    public static byte[] getMD5(File file) {

        byte[] desc = new byte[16];
        try {
            InputStream ins = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            int len;
            while((len = ins.read(buffer)) != -1){
                md5.update(buffer, 0, len);
            }
            ins.close();
            byte[] source = md5.digest();
            System.arraycopy(source, 0, desc, 0, desc.length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return desc;
    }

    public static int getInt(byte[] bytes, final int offset, int length) {
        if (null == bytes) {
            return 0;
        }
        final int bytesLen = bytes.length;
        if (bytesLen == 0 || offset < 0 || bytesLen <= offset) {
            return 0;
        }
        if (length > bytesLen - offset) {
            length = bytesLen - offset;
        }

        int value = 0;
        for (int i = length + offset - 1; i >= offset; i--) {
            value = (value << 8 | (bytes[i] & 0xff));
        }
        return value;
    }

    public static class FileInfo {
        public boolean isExist;
        public int fileLength;
        public String filename;
        public byte[] md5;

        public static FileInfo parse(byte[] data) {
            if (data[0] != CMD_FILE_INFO) {
                return null;
            }

            StringBuffer sb = new StringBuffer("mop_cmd_file:");
            for (int i = 0; i < data.length; i++) {
                sb.append(data[i] + ",");
            }

            FileInfo info = new FileInfo();
            info.isExist = data[8] == 1;
            info.fileLength = getInt(data, 9, 4);
            byte[] d = new byte[32];
            System.arraycopy(data, 13, d, 0, 32);
            info.filename = getString(d);
            byte[] md5 = new byte[16];
            System.arraycopy(data, 45, md5, 0, 16);
            info.md5 = md5;
            return info;
        }

        public byte[] getHeader() {
            byte[] buff = new byte[61];
            buff[0] = CMD_FILE_INFO;
            buff[1] = (byte) 0xFF;
            buff[4] = 0x35;
            buff[8] = 0;
            buff[9] = (byte) (fileLength >> 0 & 0xff);
            buff[10] = (byte) (fileLength >> 8 & 0xff);
            buff[11] = (byte) (fileLength >> 16 & 0xff);
            buff[12] = (byte) (fileLength >> 24 & 0xff);

            byte[] chars = filename.getBytes();
            System.arraycopy(chars, 0, buff, 13, chars.length);
            System.arraycopy(md5, 0, buff, 45, 16);
            if (chars.length < 32) {
                buff[13 + chars.length] = '\0';
            }
            return buff;
        }

        public static String getString(byte[] bytes) {
            if (null == bytes) {
                return "";
            }
            // 去除NULL字符
            byte zero = 0x00;
            byte no = (byte)0xFF;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == zero || bytes[i] == no) {
                    bytes = readBytes(bytes, 0, i);
                    break;
                }
            }
            return new String(bytes, Charset.forName("GBK"));
        }

        public static byte[] readBytes(byte[] source, int from, int length) {
            byte[] result = new byte[length];
            System.arraycopy(source, from, result, 0, length);
            /**
             for (int i = 0; i < length; i++) {
             result[i] = source[from + i];
             }
             */
            return result;
        }

        @Override
        public String toString() {
            return "FileInfo{" +
                    "isExist=" + isExist +
                    ", fileLength=" + fileLength +
                    ", filename='" + filename + '\'' +
                    ", md5=" + Arrays.toString(md5) +
                    '}';
        }
    }

    public interface ProcessCallback {
        void callback(int length);
    }

    public static class FileTransResult {
        // true代表读了多少数据(length)，false重新读写，并根据之后length个字节锁代表的int值去移动游标到指定位置
        public boolean success;
        public int length;

        public FileTransResult(boolean success, int length) {
            this.success = success;
            this.length = length;
        }
    }

    public static class TipEvent {
        public static final int UPLOAD = 0;
        public static final int DOWNLOAD = 1;

        public TipEvent(int type) {
            this.type = type;
        }

        public int type;
        public String state;
        public String result;
        public String progress;
    }
}
