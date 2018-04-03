package com.icebreaker.wimt;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 1101;
    private ListView appList;
    private AppAdapter appAdapter;
    private List<AppInfo> appInfos;
    private Calendar beginCal;
    private int state = 0;//查询应用状态码，默认为0，为查询今日状态，为1时查询指定日期状态
    private BroadcastReceiver screenUnlockReceiver;
    private int unlockCount = 1;
    private TextView countText;
    private MyDBOpenHelper myDBOpenHelper;
    private SQLiteDatabase db;

    //检测用户是否对本app开启了“Apps with usage access”权限
    private boolean hasPermission() {
        AppOpsManager appOps = (AppOpsManager)
                getSystemService(Context.APP_OPS_SERVICE);
        int mode = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }


    final Handler unlockHandler = new Handler()
    {
        @Override
        //重写handleMessage方法,根据msg中what的值判断是否执行后续操作
        public void handleMessage(Message msg) {
            if(msg.what == 0x123)
            {
                updateDataBase(1);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initApp();
        updateDataBase(0);

        countText = findViewById(R.id.count_text);
        setTextView();

        //设置起始时间为今日0点结束时间为当前时间
        beginCal = Calendar.getInstance();
        beginCal.set(Calendar.HOUR_OF_DAY,0);
        beginCal.set(Calendar.MINUTE,0);
        beginCal.set(Calendar.SECOND,0);
        //获取应用使用情况
        appInfos = getInformation(beginCal,state);
        //初始化ListView
        appList = findViewById(R.id.app_list);
        appAdapter = new AppAdapter();
        appList.setAdapter(appAdapter);
        appAdapter.initAppAdapter(appInfos,MainActivity.this);
    }


    //获取应用信息
    private List<AppInfo> getInformation(Calendar beginCal, int state){
        Calendar endCal;
        if(state==0){
            endCal = Calendar.getInstance();
        }else{
            endCal = Calendar.getInstance();
            endCal.set(Calendar.YEAR,beginCal.get(Calendar.YEAR));
            endCal.set(Calendar.MONTH,beginCal.get(Calendar.MONTH));
            endCal.set(Calendar.DAY_OF_MONTH,beginCal.get(Calendar.DAY_OF_MONTH));
            endCal.set(Calendar.HOUR_OF_DAY,23);
            endCal.set(Calendar.MINUTE,59);
            endCal.set(Calendar.SECOND,59);
        }

        UsageStatsManager manager=(UsageStatsManager)getApplicationContext().getSystemService(USAGE_STATS_SERVICE);
        List<UsageStats> stats=manager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY,beginCal.getTimeInMillis(),endCal.getTimeInMillis());
        List<AppInfo> appInfoList = new ArrayList<AppInfo>();
        for(UsageStats us:stats){
            try {
                PackageManager pm=getApplicationContext().getPackageManager();
                ApplicationInfo applicationInfo=pm.getApplicationInfo(us.getPackageName(), PackageManager.GET_META_DATA);
                //过滤掉系统应用
                //if((applicationInfo.flags&applicationInfo.FLAG_SYSTEM)<=0){
                    AppInfo appInfo = new AppInfo();
                    if (applicationInfo.loadIcon(pm) != null) {
                        appInfo.setIcon(applicationInfo.loadIcon(pm));
                    }
                    appInfo.setAppName(applicationInfo.loadLabel(pm).toString());
                    appInfo.setAppPackage(applicationInfo.packageName);

                    //将毫秒转化为秒存储
                    appInfo.setForegroundTime(us.getTotalTimeInForeground()/1000);
                    appInfo.setLaunchCount(us.getClass().getDeclaredField("mLaunchCount").getInt(us));
                    //过滤未启动过的应用
                    if(appInfo.getLaunchCount()>0){
                        appInfoList.add(appInfo);
                    }
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return appInfoList;
    }

    private void setTextView(){
        countText.setText("屏幕解锁"+unlockCount+"次");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS) {
            if (!hasPermission()) {
                //若用户未开启权限，则引导用户开启“Apps with usage access”权限
                startActivityForResult(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS);
            }
        }
    }

    private void initApp(){
        //检测用户是否对本app开启了“Apps with usage access”权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!hasPermission()) {
                startActivityForResult(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS);
            }
        }
        //屏幕解锁计数
        screenUnlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    unlockCount++;
                    setTextView();
                    unlockHandler.sendEmptyMessage(0x123);
                }
            }
        };
        IntentFilter itFilter = new IntentFilter();
        itFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenUnlockReceiver, itFilter);
    }
    private void updateDataBase(int state){
        //数据库操作
        myDBOpenHelper = new MyDBOpenHelper(MainActivity.this,"wimt.db",null,1);
        db = myDBOpenHelper.getWritableDatabase();
        Calendar todayCal = Calendar.getInstance();
        String date = String.valueOf(todayCal.get(Calendar.YEAR)+" "+(todayCal.get(Calendar.MONTH)+1)+" "+todayCal.get(Calendar.DAY_OF_MONTH));
        String sql = "SELECT count FROM unlockCount WHERE date = ?";
        Cursor cursor = db.rawQuery(sql,new String[]{date});
        if (cursor.moveToFirst()){
            if(state == 0){
                unlockCount = cursor.getInt(cursor.getColumnIndex("count"));
            }

        }else{
            String createSql = "INSERT INTO  unlockCount(date,count) VALUES(?,?)";
            db.execSQL(createSql,new String[]{date,String.valueOf(unlockCount)});
        }
        if(state == 1){
            String updateSql = "UPDATE unlockCount SET count = ? WHERE date = ?";
            db.execSQL(updateSql,new String[]{String.valueOf(unlockCount),date});
        }
        cursor.close();
    }

    @Override
    protected  void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenUnlockReceiver);
        if(myDBOpenHelper!=null){
            myDBOpenHelper.close();
        }
    }
}
