package dl.dq.com.downloadutil;

/**
 * @author liqing on 18-3-16.
 */

public class DownloadManager {
    private DownloadTask downloadTask;
    public DownloadManager(String path, String fileName, String downloadUrl){
        downloadTask = new DownloadTask(path,fileName,downloadUrl);
    }

    public void start(){
        downloadTask.execute();
    }

    public void stop(){
        downloadTask.stop();
    }

    public void restart(){
        start();
    }

    public void setCallback(DownloadTask.Callback callback){
        downloadTask.setCallback(callback);
    }
}
