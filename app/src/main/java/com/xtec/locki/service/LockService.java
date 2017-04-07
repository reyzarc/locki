package com.xtec.locki.service;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xtec.locki.Constant;
import com.xtec.locki.activity.UnlockByFingerprintActivity;
import com.xtec.locki.activity.UnlockByGestureActivity;
import com.xtec.locki.activity.UnlockByNumberActivity;
import com.xtec.locki.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;

/**
 * Created by 武昌丶鱼 on 2017/3/24.
 * Description:
 */

public class LockService extends AccessibilityService {
    private CharSequence mWindowClassName;
    /**
     * 超时时间
     */
    private static final int Timeout = 5 * 60 * 1000;
    /**
     * 当前应用包名
     */
    private String mCurrentPackage;
    /**
     * 需要验证的应用包名
     */
    private String mTargetPackage;
    private MyBroadcastReceiver mReceiver;
    /**
     * 不加锁的应用列表
     */
//    private String[] mFilterPackage = new String[]{"com.google.android.googlequicksearchbox", "com.android.systemui", "com.xtec.locki", "com.cyou.privacysecurity"};
    private String[] mFilterPackage = new String[]{ "com.xtec.locki"};
    private List<String> mLockList = new ArrayList<>();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        switch (type) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                mWindowClassName = event.getClassName();
                mCurrentPackage = event.getPackageName() == null ? "" : event.getPackageName().toString();
                Log.e("reyzarc", mCurrentPackage + "目标包名是---->" + mTargetPackage);


                //判断当前将要打开的应用是否跟之前的应用包名不一样,如果不一样,则将之前的应用锁重置
                //如果应用包名一样,再判断锁是否超时,如果超时,则也要将应用锁重置,超时时间为5分钟
                Long currentTime = System.currentTimeMillis();
                Log.e("reyzarc", "时间差为---->" + currentTime + "------->" + PreferenceUtils.getLong(this, mCurrentPackage + "time"));
                if (!TextUtils.equals(mCurrentPackage, mTargetPackage)) {
                    PreferenceUtils.putBoolean(this, mTargetPackage, false);
                } else if (currentTime - PreferenceUtils.getLong(this, mTargetPackage + "time") > Timeout) {
                    PreferenceUtils.putBoolean(this, mTargetPackage, false);
                    PreferenceUtils.putLong(this, mTargetPackage + "time", System.currentTimeMillis());
                }
                //过滤掉像系统锁屏界面/launcher等
                for (int i = 0; i < mFilterPackage.length; i++) {
                    if (TextUtils.equals(mCurrentPackage, mFilterPackage[i])) {
                        return;
                    }
                }
                //如果不在加锁列表中,也不需要处理
                if (!checkInList(mCurrentPackage)) {
                    return;
                }
                mTargetPackage = mCurrentPackage;
                checkLockStatus(mTargetPackage);

                break;
            case TYPE_VIEW_CLICKED:
            case TYPE_VIEW_LONG_CLICKED:
                break;
        }
    }

    private void checkLockStatus(String targetPackage) {
        if (!PreferenceUtils.getBoolean(this, targetPackage)) {
            Intent intent = new Intent();
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Constant.PACKAGE_NAME, targetPackage);
            switch (PreferenceUtils.getString(this, Constant.LOCK_METHOD)) {
                case Constant.FINGERPRINT://指纹
                    intent.setClass(this, UnlockByFingerprintActivity.class);
                    break;
                case Constant.GESTURE://手势
                    intent.setClass(this, UnlockByGestureActivity.class);
                    break;
                case Constant.NUMBER://密码
                default:
                    intent.setClass(this, UnlockByNumberActivity.class);
                    break;
            }
            startActivity(intent);
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        //获取保存的加锁列表
        getLockList();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constant.ACTION_UNLOCK_SUCCESS);
        filter.addAction(Constant.ACTION_UPDATE_UNLOCK_LIST);
        mReceiver = new MyBroadcastReceiver();
        registerReceiver(mReceiver, filter);

        IntentFilter mScreenOnFilter = new IntentFilter("android.intent.action.SCREEN_ON");
        registerReceiver(mScreenOReceiver, mScreenOnFilter);

        /* 注册机器锁屏时的广播 */
        IntentFilter mScreenOffFilter = new IntentFilter("android.intent.action.SCREEN_OFF");
        registerReceiver(mScreenOReceiver, mScreenOffFilter);
    }

    private void getLockList() {
        String str = PreferenceUtils.getString(this, Constant.LOCK_LIST);
        if (!TextUtils.isEmpty(str)) {
            Gson gson = new Gson();
            mLockList = gson.fromJson(str, new TypeToken<List<String>>() {
            }.getType());
        }
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String packageName = intent.getStringExtra(Constant.PACKAGE_NAME);
            if (!TextUtils.isEmpty(action) && TextUtils.equals(action, Constant.ACTION_UNLOCK_SUCCESS)) {//解锁成功
                Log.e("reyzarc", "解锁成功....." + packageName);
                PreferenceUtils.putBoolean(LockService.this, packageName, true);
                //保存时间,以当前加锁应用的包名为key
//                PreferenceUtils.putLong(LockService.this,packageName, System.currentTimeMillis(),true);
            } else if (!TextUtils.isEmpty(action) && TextUtils.equals(action, Constant.ACTION_UPDATE_UNLOCK_LIST)) {//更新加锁列表
                getLockList();
            }
        }
    }

    /**
     * 锁屏的管理类叫KeyguardManager，
     * 通过调用其内部类KeyguardLockmKeyguardLock的对象的disableKeyguard方法可以取消系统锁屏，
     * newKeyguardLock的参数用于标识是谁隐藏了系统锁屏
     */
    private BroadcastReceiver mScreenOReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.SCREEN_ON")) {//亮屏
                Log.e("reyzarc", "—— SCREEN_ON ——");
            } else if (action.equals("android.intent.action.SCREEN_OFF")) {//熄屏
                //将锁重置为未解锁状态
                Log.e("reyzarc", "—— SCREEN_OFF ——" + mTargetPackage);
                if (!TextUtils.isEmpty(mTargetPackage)) {
                    PreferenceUtils.putBoolean(LockService.this, mTargetPackage, false);
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    /**
     * 判断是否在加锁列表中
     *
     * @param packageName
     * @return
     */
    public boolean checkInList(String packageName) {
        if (!mLockList.isEmpty()) {
            for (int i = 0; i < mLockList.size(); i++) {
                if (TextUtils.equals(mLockList.get(i), packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

}
