package dl.dq.com.downloadutil;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author liqing on 18-3-16.
 */

public class DownloadTask extends Handler {

    private final static String TAG = "DownloadTask";
    private final static long CONNECT_TIMEOUT = 60;
    private final static long READ_TIMEOUT = 60;
    private final static long WRITE_TIMEOUT = 60;

    private static final int MSG_IDLE = 1000;
    private static final int MSG_START = 1001;
    private static final int MSG_DOWNLOADING = 1002;
    private static final int MSG_FINISH = 1003;
    private int staus = MSG_IDLE;

    private final int THREAD_COUNT = 2;

    private long mFileLength;
    private long mThreadCompleteCount;
    private OkHttpClient mOkHttpClient;
    private File mTmpFile;
    private String mTmpFilePath;
    private String mTmpFileName;
    private String mDownloadUrl;
    private List<File> cacheFiles = new ArrayList<>();
    private Callback callback;
    private Map<Long,Long> eachThreadDownloaded = new HashMap<Long,Long>();
    private double downloadingSpeed;

    public DownloadTask(String path, String fileName, String downloadUrl) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS);
        if (downloadUrl == null ||
                fileName == null ||
                path == null) {
            throw new NullPointerException("Parameter is null");
        }
        mOkHttpClient = builder.build();
        mTmpFileName = fileName;
        mTmpFilePath = path;
        mDownloadUrl = downloadUrl;
    }

    public void execute() {
        if (staus > MSG_IDLE && staus < MSG_FINISH) {
            return;
        }
        staus = MSG_START;
        sendMessage(MSG_START);
        Request request = new Request.Builder()
                .url(mDownloadUrl)
                .build();
        //异步执行会话请求
        getCall(request).enqueue(new OkResponse());
    }

    public void stop(){
        staus = MSG_IDLE;
    }

    /**
     * 创建请求会话
     */
    private Call getCall(Request request) {
        Call call = mOkHttpClient.newCall(request);
        return call;
    }

    private void requestFileLengthAndDownload(Response response) throws IOException {
        mFileLength = response.body().contentLength();
        if (mFileLength < 0) {
            Log.d(TAG, "The server does not support multicast.");
            return;
        }
        touchTmpFile(mFileLength);

        // 计算每个线程开始下载的位置
        long blockSize = mFileLength / THREAD_COUNT;
        for (int i = 0; i < THREAD_COUNT; i++) {
            long slicer = i * blockSize;
            long endIndex = (i + 1) * blockSize - 1;
            if (i == (THREAD_COUNT - 1)) {
                endIndex = mFileLength - 1;
            }

            long downloaded = getDownloaded(slicer);
            final long startIndex = downloaded != 0 ? downloaded : slicer;
            // 开启多线程下载
            dispatchTask(startIndex, endIndex, slicer);
        }
    }

    /**
     * 如果已经下载过,重新设置下载起点
     */
    private long getDownloaded(long startIndex) throws IOException {
        long downloaded = 0;
        final File cacheFile = new File(mTmpFilePath, +startIndex + "_" + mTmpFileName + ".cache");
        final RandomAccessFile cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");

        String startIndexStr = cacheAccessFile.readLine();

        if (startIndexStr != null) {
            downloaded = Long.parseLong(startIndexStr);
        }
        cacheAccessFile.close();
        return downloaded;
    }

    class OkResponse implements okhttp3.Callback {
        long slicer;

        OkResponse() {
        }

        OkResponse(long slicer) {
            this.slicer = slicer;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            int code = response.code();
            switch (code) {
                case 200:
                    requestFileLengthAndDownload(response);
                    break;
                case 206:
                    downloadResponse(response, slicer);
                    break;
            }
        }
    }

    private void touchTmpFile(long fileLength) {
        mTmpFile = new File(mTmpFilePath, mTmpFileName + ".tmp");
        if (!mTmpFile.getParentFile().exists()) {
            mTmpFile.getParentFile().mkdirs();
        }
        try {
            RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
            tmpAccessFile.setLength(fileLength);
            tmpAccessFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dispatchTask(long start, long end, long slicer) throws IOException {
        Request request = new Request.Builder().header("RANGE", "bytes=" + start + "-" + end)
                .url(mDownloadUrl)
                .build();
        getCall(request).enqueue(new OkResponse(slicer));
    }

    private void downloadResponse(Response response, long slicer) throws IOException {
        Log.d(TAG, Thread.currentThread().getName() + response.header("Content-range"));
        long start = Long.parseLong(response.header("Content-range").
                replaceAll("bytes ", "").split("-")[0]);
        // 查找多线程分割的缓冲文件
        final File cacheFile =
                new File(mTmpFilePath, +slicer + "_" + mTmpFileName + ".cache");
        final RandomAccessFile cacheAccessFile = new RandomAccessFile(cacheFile, "rwd");

        download(response, cacheAccessFile, start);
        cacheFiles.add(cacheFile);
        sendMessage(MSG_FINISH);
    }

    private void download(Response response, RandomAccessFile cacheAccessFile, long startIndex) throws IOException {
        InputStream is = response.body().byteStream();
        RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
        // 已经下载好的
        final long downloaded = startIndex;
        // 如果下载的文件有问题,先看每个线程下载的起始位置是否有问题(startIndex)
        tmpAccessFile.seek(startIndex);
        staus = MSG_DOWNLOADING;

        // 文件流写入本地
        byte[] buffer = new byte[1024 << 2];
        int writeTotal = 0;
        int length;
        long total;
        long startTime;
        long endTime;
        while ((length = is.read(buffer)) > 0) {
            startTime = System.currentTimeMillis();
            tmpAccessFile.write(buffer, 0, length);
            writeTotal += length;
            total = downloaded + writeTotal;
            if (cacheAccessFile != null) {
                cacheAccessFile.seek(0);
                cacheAccessFile.write((total + "").getBytes("UTF-8"));
            }

            eachThreadDownloaded.put(startIndex,total - startIndex);
            endTime = System.currentTimeMillis();
            if (endTime>startTime){
                downloadingSpeed = length/ (endTime-startTime);
            }
            sendMessage(MSG_DOWNLOADING);
            if (staus == MSG_IDLE){
                break;
            }
        }
        //关闭资源
        close(cacheAccessFile, is, tmpAccessFile, response.body());
    }

    /**
     * 关闭资源
     */
    private void close(Closeable... closeables) {
        int length = closeables.length;
        try {
            for (int i = 0; i < length; i++) {
                Closeable closeable = closeables[i];
                if (null != closeable)
                    closeables[i].close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < length; i++) {
                closeables[i] = null;
            }
        }
    }

    /**
     * 删除记录文件
     */
    private void deleteFile(List<File> files) {
        for (int i = 0; i < files.size(); i++) {
            if (null != files.get(i))
                files.get(i).delete();
        }
    }

    private void sendMessage(int what) {
        Message message = Message.obtain();
        message.what = what;
        sendMessage(message);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case MSG_START:
                if (callback != null) {
                    callback.start();
                }
                break;
            case MSG_DOWNLOADING:
                if (callback != null) {
                    long total = 0;
                    for (Map.Entry<Long,Long> entry : eachThreadDownloaded.entrySet()){
                        total += entry.getValue();
                    }
                    callback.downloading(getProgress(total,mFileLength),willTakeTime(mFileLength-total));
                //Log.d(TAG,mFileLength-total +"=="+willTakeTime(mFileLength-total)/1000+"");
                }
                break;
            case MSG_FINISH:
                mThreadCompleteCount++;
                Log.d(TAG, "download "+mThreadCompleteCount);
                if (mThreadCompleteCount % THREAD_COUNT != 0) {
                    return;
                }
                //下载完毕后，重命名目标文件名
                mTmpFile.renameTo(new File(mTmpFilePath, mTmpFileName));
                deleteFile(cacheFiles);
                staus = MSG_FINISH;
                Log.d(TAG, "download finish!");
                if (callback != null){
                    callback.finish();
                }
                break;
        }
    }

    private int getProgress(long top, long below) {
        double result = new BigDecimal((float)top / below)
                .setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        return (int)result * 100;
    }

    private double willTakeTime(long remain){
        return remain / downloadingSpeed;
    }

    public interface Callback {
        public void start();

        /**
         * @param progress return 1~100
         * @param willTakeTime return s
         */
        public void downloading(int progress, double willTakeTime);

        public void finish();
    }

    public void setCallback(Callback callback){
        this.callback = callback;
    }
}
