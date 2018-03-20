package dl.dq.com.downloadutil;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);

        DownloadManager downloadManager = new DownloadManager(getSDPath()+"/liqing/",
                "aa.png",
                "http://imgrepo-cnbj.devops.letv.com/dailybuild/SuperTV/demeter/cn/aosp_mangosteen_x4n/daily/20180315/x440n_demeter_final_V2406RCN02C065287D03151D_20180315_220232_cibn_userdebug/aosp_mangosteen_x4n_ota_V2406RCN02C065287D03151D/LetvUpgrade938-X4N.bin");
        downloadManager.setCallback(new CallbackImp());
        downloadManager.start();

//        DownloadManager.start(getSDPath()+"/liqing/",
//                "bb.jpg",
//                "http://imgsrc.baidu.com/imgad/pic/item/83025aafa40f4bfb978dd06f084f78f0f73618af.jpg");
//        DownloadManager.start(getSDPath()+"/liqing/",
//                "LetvUpgrade938-X4N.zip",
//                "http://imgrepo-cnbj.devops.letv.com/dailybuild/SuperTV/demeter/cn/aosp_mangosteen_x4n/daily/20180315/x440n_demeter_final_V2406RCN02C065287D03151D_20180315_220232_cibn_userdebug/aosp_mangosteen_x4n_ota_V2406RCN02C065287D03151D/LetvUpgrade938-X4N.bin");

    }

    class CallbackImp implements DownloadTask.Callback{

        @Override
        public void start() {
            Log.d(TAG,"start");
        }

        @Override
        public void downloading(int progress, double willTakeTime) {
            Log.d(TAG,progress + "---"+willTakeTime);
        }

        @Override
        public void finish() {
            Log.d(TAG,"finish");
        }
    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);

        }
    }

    public static String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED);// 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
        }
        return sdDir.toString();
    }
}
