package com.lgh.advertising.going;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.lgh.advertising.myactivity.EditDataActivity;
import com.lgh.advertising.myclass.AppDescribe;
import com.lgh.advertising.myclass.AutoFinder;
import com.lgh.advertising.myclass.Coordinate;
import com.lgh.advertising.myclass.DataBridge;
import com.lgh.advertising.myclass.DataDao;
import com.lgh.advertising.myclass.DataDaoFactory;
import com.lgh.advertising.myclass.Widget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainFunction {

    private WindowManager windowManager;
    private PackageManager packageManager;
    private DataDao dataDao;
    private Map<String, AppDescribe> appDescribeMap;
    private AppDescribe appDescribe;
    private AccessibilityService service;
    private String currentPackage;
    private String currentActivity;
    private boolean on_off_coordinate, on_off_widget, on_off_autoFinder;
    private int autoRetrieveNumber;
    private AccessibilityServiceInfo serviceInfo;
    private ScheduledFuture future_coordinate, future_widget, future_autoFinder;
    private ScheduledExecutorService executorService;
    private MyScreenOffReceiver screenOffReceiver;
    private Set<Widget> widgetSet;
    private MyInstallReceiver installReceiver;

    private WindowManager.LayoutParams aParams, bParams, cParams;
    private View viewDataShow, viewLayoutAnalyze;
    private ImageView viewClickPosition;

    public MainFunction(AccessibilityService service) {
        this.service = service;
    }

    protected void onServiceConnected() {
        try {
            windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
            packageManager = service.getPackageManager();
            currentPackage = "Initialize CurrentPackage";
            currentActivity = "Initialize CurrentActivity";
            executorService = Executors.newSingleThreadScheduledExecutor();
            serviceInfo = service.getServiceInfo();
            dataDao = DataDaoFactory.getInstance(service);
            screenOffReceiver = new MyScreenOffReceiver();
            service.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            installReceiver = new MyInstallReceiver();
            IntentFilter filterInstall = new IntentFilter();
            filterInstall.addAction(Intent.ACTION_PACKAGE_ADDED);
            filterInstall.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            filterInstall.addDataScheme("package");
            service.registerReceiver(installReceiver, filterInstall);
            updatePackage();
            future_coordinate = future_widget = future_autoFinder = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                }
            }, 0, TimeUnit.MILLISECONDS);
        } catch (Throwable throwable) {
//            throwable.printStackTrace();
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                    AccessibilityNodeInfo node = service.getRootInActiveWindow();
                    if (node == null || node.getPackageName().equals(currentPackage)) {
                        break;
                    }
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    AccessibilityNodeInfo root = service.getRootInActiveWindow();
                    CharSequence temPackage = event.getPackageName();
                    CharSequence temClass = event.getClassName();
                    String packageName = root != null ? root.getPackageName().toString() : temPackage != null ? temPackage.toString() : null;
                    String activityName = temClass != null ? temClass.toString() : null;
                    if (root != null && temPackage != null) {
                        if (!root.getPackageName().equals(temPackage)) {
                            activityName = root.getPackageName() + ".unknownActivity";
                        }
                    }
                    if (packageName != null) {
                        if (!packageName.equals(currentPackage)) {
                            appDescribe = appDescribeMap.get(packageName);
                            if (appDescribe != null) {
                                currentPackage = packageName;
                                if (appDescribe.on_off) {
                                    future_coordinate.cancel(false);
                                    future_widget.cancel(false);
                                    future_autoFinder.cancel(false);
                                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                    service.setServiceInfo(serviceInfo);
                                    on_off_coordinate = appDescribe.coordinateOnOff;
                                    on_off_widget = appDescribe.widgetOnOff;
                                    on_off_autoFinder = appDescribe.autoFinderOnOFF;
                                    autoRetrieveNumber = 0;

                                    if (on_off_coordinate && !appDescribe.coordinateRetrieveAllTime) {
                                        future_coordinate = executorService.schedule(new Runnable() {
                                            @Override
                                            public void run() {
                                                on_off_coordinate = false;
                                            }
                                        }, appDescribe.coordinateRetrieveTime, TimeUnit.MILLISECONDS);
                                    }

                                    if (on_off_widget && !appDescribe.widgetRetrieveAllTime) {
                                        future_widget = executorService.schedule(new Runnable() {
                                            @Override
                                            public void run() {
                                                on_off_widget = false;
                                                if (!on_off_autoFinder) {
                                                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                                    service.setServiceInfo(serviceInfo);
                                                }
                                            }
                                        }, appDescribe.widgetRetrieveTime, TimeUnit.MILLISECONDS);
                                    }

                                    if (on_off_autoFinder) {
                                        serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                        service.setServiceInfo(serviceInfo);
                                        if (!appDescribe.autoFinderRetrieveAllTime) {
                                            future_autoFinder = executorService.schedule(new Runnable() {
                                                @Override
                                                public void run() {
                                                    on_off_autoFinder = false;
                                                    if (!on_off_widget) {
                                                        serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                                        service.setServiceInfo(serviceInfo);
                                                    }
                                                }
                                            }, appDescribe.autoFinderRetrieveTime, TimeUnit.MILLISECONDS);
                                        }
                                    }

                                } else {
                                    if (on_off_coordinate || on_off_widget || on_off_autoFinder) {
                                        serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                        service.setServiceInfo(serviceInfo);
                                        on_off_coordinate = false;
                                        on_off_widget = false;
                                        on_off_autoFinder = false;
                                        future_coordinate.cancel(false);
                                        future_widget.cancel(false);
                                        future_autoFinder.cancel(false);
                                    }
                                }
                            }
                        }
                    }
                    if (activityName != null) {
                        if (!activityName.startsWith("android.widget.") && !activityName.startsWith("android.view.")) {
                            currentActivity = activityName;
                            if (appDescribe != null) {
                                if (on_off_coordinate) {
                                    final Coordinate coordinate = appDescribe.coordinateMap.get(activityName);
                                    if (coordinate != null) {
                                        executorService.scheduleAtFixedRate(new Runnable() {
                                            int num = 0;

                                            @Override
                                            public void run() {
                                                if (num < coordinate.clickNumber && currentActivity.equals(coordinate.appActivity)) {
                                                    click(coordinate.xPosition, coordinate.yPosition, 0, 20);
                                                    num++;
                                                } else {
                                                    throw new RuntimeException();
                                                }
                                            }
                                        }, coordinate.clickDelay, coordinate.clickInterval, TimeUnit.MILLISECONDS);
                                    }
                                }
                                if (on_off_widget) {
                                    widgetSet = appDescribe.widgetSetMap.get(activityName);
                                    if (widgetSet != null) {
                                        serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                        service.setServiceInfo(serviceInfo);
                                    } else if (!on_off_autoFinder) {
                                        serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                        service.setServiceInfo(serviceInfo);
                                    }
                                }
                            }
                        }
                    }
                    if (packageName != null && packageName.equals(currentPackage)) {
                        if (on_off_widget && appDescribe != null && widgetSet != null) {
                            findButtonByWidget(root, widgetSet);
                        }
                        if (on_off_autoFinder && appDescribe != null) {
                            findButtonByText(root, appDescribe.autoFinder);
                        }
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if (event.getPackageName().equals(currentPackage)) {
                        AccessibilityNodeInfo source = event.getSource();
                        if (on_off_widget && appDescribe != null && widgetSet != null) {
                            findButtonByWidget(source, widgetSet);
                        }
                        if (on_off_autoFinder && appDescribe != null) {
                            findButtonByText(source, appDescribe.autoFinder);
                        }
                    }
                    break;
            }
        } catch (Throwable throwable) {
//            throwable.printStackTrace();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        try {
            if (viewDataShow != null && viewClickPosition != null && viewLayoutAnalyze != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                aParams.x = (metrics.widthPixels - aParams.width) / 2;
                aParams.y = metrics.heightPixels - aParams.height;
                bParams.width = metrics.widthPixels;
                bParams.height = metrics.heightPixels;
                cParams.x = (metrics.widthPixels - cParams.width) / 2;
                cParams.y = (metrics.heightPixels - cParams.height) / 2;
                windowManager.updateViewLayout(viewDataShow, aParams);
                windowManager.updateViewLayout(viewClickPosition, cParams);
                FrameLayout layout = viewLayoutAnalyze.findViewById(R.id.frame);
                layout.removeAllViews();
                TextView text = new TextView(service);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                text.setGravity(Gravity.CENTER);
                text.setTextColor(0xffff0000);
                text.setText("请重新刷新布局");
                windowManager.updateViewLayout(layout, bParams);
                layout.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            }
        } catch (Throwable e) {
//            e.printStackTrace();
        }

    }

    public boolean onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(screenOffReceiver);
            service.unregisterReceiver(installReceiver);
        } catch (Throwable e) {
//            e.printStackTrace();
        }
        return true;
    }

    /**
     * 自动查找启动广告的
     * “跳过”的控件
     */
    private void findButtonByText(AccessibilityNodeInfo nodeInfo, final AutoFinder autoFinder) {
        try {
            if (nodeInfo == null) return;
            for (int n = 0; n < autoFinder.keywordList.size(); n++) {
                final List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(autoFinder.keywordList.get(n));
                if (!list.isEmpty()) {
                    executorService.schedule(new Runnable() {
                        @Override
                        public void run() {
                            if (autoFinder.clickOnly) {
                                for (AccessibilityNodeInfo e : list) {
                                    if (e.refresh()) {
                                        Rect rect = new Rect();
                                        e.getBoundsInScreen(rect);
                                        click(rect.centerX(), rect.centerY(), 0, 20);
                                    }
                                }
                            } else {
                                for (AccessibilityNodeInfo e : list) {
                                    if (e.refresh()) {
                                        if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                            if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                Rect rect = new Rect();
                                                e.getBoundsInScreen(rect);
                                                click(rect.centerX(), rect.centerY(), 0, 20);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }, autoFinder.clickDelay, TimeUnit.MILLISECONDS);
                    if (++autoRetrieveNumber >= autoFinder.retrieveNumber) {
                        on_off_autoFinder = false;
                        if (!on_off_widget) {
                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                            service.setServiceInfo(serviceInfo);
                        }
                    }
                    return;
                }

            }
            nodeInfo.recycle();
        } catch (Throwable e) {
//            e.printStackTrace();
        }
    }

    /**
     * 查找并点击由
     * Widget
     * 定义的控件
     */
    private void findButtonByWidget(AccessibilityNodeInfo root, Set<Widget> set) {
        try {
            int a = 0;
            int b = 1;
            ArrayList<AccessibilityNodeInfo> listA = new ArrayList<>();
            ArrayList<AccessibilityNodeInfo> listB = new ArrayList<>();
            listA.add(root);
            while (a < b) {
                final AccessibilityNodeInfo node = listA.get(a++);
                if (node != null) {
                    final Rect temRect = new Rect();
                    node.getBoundsInScreen(temRect);
                    CharSequence cId = node.getViewIdResourceName();
                    CharSequence cDescribe = node.getContentDescription();
                    CharSequence cText = node.getText();
                    for (final Widget e : set) {
                        boolean isFind = false;
                        if (temRect.equals(e.widgetRect)) {
                            isFind = true;
                        } else if (cId != null && !e.widgetId.isEmpty() && cId.toString().equals(e.widgetId)) {
                            isFind = true;
                        } else if (cDescribe != null && !e.widgetDescribe.isEmpty() && cDescribe.toString().contains(e.widgetDescribe)) {
                            isFind = true;
                        } else if (cText != null && !e.widgetText.isEmpty() && cText.toString().contains(e.widgetText)) {
                            isFind = true;
                        }
                        if (isFind) {
                            executorService.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (node.refresh()) {
                                        if (e.clickOnly) {
                                            click(temRect.centerX(), temRect.centerY(), 0, 20);
                                        } else {
                                            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                if (!node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                    click(temRect.centerX(), temRect.centerY(), 0, 20);
                                                }
                                            }
                                        }
                                    }
                                }
                            }, e.clickDelay, TimeUnit.MILLISECONDS);
                            break;
                        }
                    }
                    for (int n = 0; n < node.getChildCount(); n++) {
                        listB.add(node.getChild(n));
                    }
                }
                if (a == b) {
                    a = 0;
                    b = listB.size();
                    listA = listB;
                    listB = new ArrayList<>();
                }
            }
        } catch (Throwable e) {
//            e.printStackTrace();
        }
    }

    /**
     * 查找所有
     * 的控件
     */
    private void findAllNode(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> list) {
        try {
            ArrayList<AccessibilityNodeInfo> temList = new ArrayList<>();
            for (AccessibilityNodeInfo e : roots) {
                if (e == null) continue;
                list.add(e);
                for (int n = 0; n < e.getChildCount(); n++) {
                    temList.add(e.getChild(n));
                }
            }
            if (!temList.isEmpty()) {
                findAllNode(temList, list);
            }
        } catch (Throwable e) {
//            e.printStackTrace();
        }
    }

    /**
     * 模拟
     * 点击
     */
    private boolean click(int X, int Y, long start_time, long duration) {
        Path path = new Path();
        path.moveTo(X, Y);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, start_time, duration));
            return service.dispatchGesture(builder.build(), null, null);
        } else {
            return false;
        }
    }

    /**
     * 接收到锁屏事件时调用
     */
    public void onScreenOff() {
        currentPackage = "ScreenOff Package";
        currentActivity = "ScreenOff Activity";
    }

    /**
     * android 7.0 以上
     * 避免无障碍服务冲突
     */
    public void closeService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.disableSelf();
        }
    }

    /**
     * 把数据暴露给其他Activity
     */
    public Map<String, AppDescribe> getAppDescribeMap() {
        return appDescribeMap;
    }

    /**
     * 开启无障碍服务时调用，
     * 更新相关包名的集合
     */
    private void updatePackage() {
        try {
            appDescribeMap = new HashMap<>();
            Set<String> packageInstall = new HashSet<>();
            Set<String> packageOff = new HashSet<>();
            Set<String> packageRemove = new HashSet<>();
            List<ResolveInfo> resolveInfoList = new ArrayList<>();
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            resolveInfoList.addAll(packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL));
            for (ResolveInfo e : resolveInfoList) {
                packageOff.add(e.activityInfo.packageName);
            }
            List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
            for (InputMethodInfo e : inputMethodInfoList) {
                packageRemove.add(e.getPackageName());
            }
            packageOff.add(service.getPackageName());
            packageRemove.add("com.android.systemui");
            intent = new Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(Uri.fromFile(new File("install.apk")), "application/vnd.android.package-archive");
            resolveInfoList.addAll(packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL));
            intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            resolveInfoList.addAll(packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL));
            List<AppDescribe> appDescribeList = new ArrayList<>();
            List<AutoFinder> autoFinderList = new ArrayList<>();
            for (ResolveInfo e : resolveInfoList) {
                String packageName = e.activityInfo.packageName;
                if (!packageRemove.contains(packageName)) {
                    packageInstall.add(packageName);
                    AppDescribe appDescribe = new AppDescribe();
                    appDescribe.appName = packageManager.getApplicationLabel(e.activityInfo.applicationInfo).toString();
                    appDescribe.appPackage = packageName;
                    if ((e.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM || packageOff.contains(packageName)) {
                        appDescribe.on_off = false;
                    }
                    appDescribeList.add(appDescribe);
                    AutoFinder autoFinder = new AutoFinder();
                    autoFinder.appPackage = packageName;
                    autoFinder.keywordList = Collections.singletonList("跳过");
                    autoFinderList.add(autoFinder);
                }
            }
            dataDao.deleteAppDescribeByNotIn(packageInstall);
            dataDao.insertAppDescribe(appDescribeList);
            dataDao.insertAutoFinder(autoFinderList);
            appDescribeList = dataDao.getAllAppDescribes();
            for (AppDescribe e : appDescribeList) {
                e.getOtherFieldsFromDatabase(dataDao);
                appDescribeMap.put(e.appPackage, e);
            }
        } catch (Throwable e) {
//            e.printStackTrace();
        }
    }

    /**
     * 创建规则时调用
     */
    @SuppressLint("ClickableViewAccessibility")
    public void showAddDataFloat() {
        try {
            if (viewClickPosition != null || viewDataShow != null || viewLayoutAnalyze != null) {
                return;
            }
            final Widget widgetSelect = new Widget();
            final Coordinate coordinateSelect = new Coordinate();
            final LayoutInflater inflater = LayoutInflater.from(service);

            viewDataShow = inflater.inflate(R.layout.view_add_data, null);
            final TextView pacName = viewDataShow.findViewById(R.id.pacName);
            final TextView actName = viewDataShow.findViewById(R.id.actName);
            final TextView widget = viewDataShow.findViewById(R.id.widget);
            final TextView xyPosition = viewDataShow.findViewById(R.id.xy);
            Button switchWid = viewDataShow.findViewById(R.id.switch_wid);
            final Button saveWidgetButton = viewDataShow.findViewById(R.id.save_wid);
            Button switchAim = viewDataShow.findViewById(R.id.switch_aim);
            final Button savePositionButton = viewDataShow.findViewById(R.id.save_aim);
            Button quitButton = viewDataShow.findViewById(R.id.quit);

            viewLayoutAnalyze = inflater.inflate(R.layout.view_widget_select, null);
            final FrameLayout layoutParent = viewLayoutAnalyze.findViewById(R.id.frame);

            viewClickPosition = new ImageView(service);
            viewClickPosition.setImageResource(R.drawable.p);

            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = metrics.heightPixels > metrics.widthPixels ? metrics.widthPixels : metrics.heightPixels;
            int height = metrics.heightPixels > metrics.widthPixels ? metrics.heightPixels : metrics.widthPixels;

            aParams = new WindowManager.LayoutParams();
            aParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            aParams.format = PixelFormat.TRANSPARENT;
            aParams.gravity = Gravity.START | Gravity.TOP;
            aParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            aParams.width = width;
            aParams.height = height / 5;
            aParams.x = (metrics.widthPixels - aParams.width) / 2;
            aParams.y = metrics.heightPixels - aParams.height;
            aParams.alpha = 0.9f;

            bParams = new WindowManager.LayoutParams();
            bParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            bParams.format = PixelFormat.TRANSPARENT;
            bParams.gravity = Gravity.START | Gravity.TOP;
            bParams.width = metrics.widthPixels;
            bParams.height = metrics.heightPixels;
            bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            bParams.alpha = 0f;

            cParams = new WindowManager.LayoutParams();
            cParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            cParams.format = PixelFormat.TRANSPARENT;
            cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            cParams.gravity = Gravity.START | Gravity.TOP;
            cParams.width = cParams.height = width / 4;
            cParams.x = (metrics.widthPixels - cParams.width) / 2;
            cParams.y = (metrics.heightPixels - cParams.height) / 2;
            cParams.alpha = 0f;

            viewDataShow.setOnTouchListener(new View.OnTouchListener() {
                int startX = 0, startY = 0, x = 0, y = 0;
                ScheduledFuture future = executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                    }
                }, 0, TimeUnit.MILLISECONDS);

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = x = Math.round(event.getRawX());
                            startY = y = Math.round(event.getRawY());
                            windowManager.getDefaultDisplay().getRealMetrics(metrics);
                            aParams.x = aParams.x < 0 ? 0 : aParams.x;
                            aParams.x = aParams.x > metrics.widthPixels - aParams.width ? metrics.widthPixels - aParams.width : aParams.x;
                            aParams.y = aParams.y < 0 ? 0 : aParams.y;
                            aParams.y = aParams.y > metrics.heightPixels - aParams.height ? metrics.heightPixels - aParams.height : aParams.y;
                            future = executorService.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (Math.abs(startX - x) < 5 && Math.abs(startY - y) < 5) {
                                        Matcher matcher = Pattern.compile("(\\w|\\.)+").matcher(pacName.getText().toString());
                                        if (matcher.find()) {
                                            DataBridge.appDescribe = appDescribeMap.get(matcher.group());
                                            if (DataBridge.appDescribe != null) {
                                                Intent intent = new Intent(service, EditDataActivity.class);
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                                service.startActivity(intent);
                                            }
                                        }
                                    }
                                }
                            }, 800, TimeUnit.MILLISECONDS);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            aParams.x = Math.round(aParams.x + (event.getRawX() - x));
                            aParams.y = Math.round(aParams.y + (event.getRawY() - y));
                            x = Math.round(event.getRawX());
                            y = Math.round(event.getRawY());
                            windowManager.updateViewLayout(viewDataShow, aParams);
                            break;
                        case MotionEvent.ACTION_UP:
                            future.cancel(false);
                            break;
                    }
                    return true;
                }
            });
            viewClickPosition.setOnTouchListener(new View.OnTouchListener() {
                int x = 0, y = 0, width = cParams.width / 2, height = cParams.height / 2;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            savePositionButton.setEnabled(true);
                            cParams.alpha = 0.9f;
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            x = Math.round(event.getRawX());
                            y = Math.round(event.getRawY());
                            break;
                        case MotionEvent.ACTION_MOVE:
                            cParams.x = Math.round(cParams.x + (event.getRawX() - x));
                            cParams.y = Math.round(cParams.y + (event.getRawY() - y));
                            x = Math.round(event.getRawX());
                            y = Math.round(event.getRawY());
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            coordinateSelect.appPackage = currentPackage;
                            coordinateSelect.appActivity = currentActivity;
                            coordinateSelect.xPosition = cParams.x + width;
                            coordinateSelect.yPosition = cParams.y + height;
                            pacName.setText(coordinateSelect.appPackage);
                            actName.setText(coordinateSelect.appActivity);
                            xyPosition.setText("X轴：" + coordinateSelect.xPosition + "    " + "Y轴：" + coordinateSelect.yPosition + "    " + "(其他参数默认)");
                            break;
                        case MotionEvent.ACTION_UP:
                            cParams.alpha = 0.5f;
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            break;
                    }
                    return true;
                }
            });
            switchWid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Button button = (Button) v;
                    if (bParams.alpha == 0) {
                        AccessibilityNodeInfo root = service.getRootInActiveWindow();
                        if (root == null) return;
                        widgetSelect.appPackage = currentPackage;
                        widgetSelect.appActivity = currentActivity;
                        layoutParent.removeAllViews();
                        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
                        roots.add(root);
                        ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
                        findAllNode(roots, nodeList);
                        Collections.sort(nodeList, new Comparator<AccessibilityNodeInfo>() {
                            @Override
                            public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
                                Rect rectA = new Rect();
                                Rect rectB = new Rect();
                                a.getBoundsInScreen(rectA);
                                b.getBoundsInScreen(rectB);
                                return rectB.width() * rectB.height() - rectA.width() * rectA.height();
                            }
                        });
                        for (final AccessibilityNodeInfo e : nodeList) {
                            final Rect temRect = new Rect();
                            e.getBoundsInScreen(temRect);
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(temRect.width(), temRect.height());
                            params.leftMargin = temRect.left;
                            params.topMargin = temRect.top;
                            final ImageView img = new ImageView(service);
                            img.setBackgroundResource(R.drawable.node);
                            img.setFocusableInTouchMode(true);
                            img.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    v.requestFocus();
                                }
                            });
                            img.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                                @Override
                                public void onFocusChange(View v, boolean hasFocus) {
                                    if (hasFocus) {
                                        widgetSelect.widgetRect = temRect;
                                        widgetSelect.widgetClickable = e.isClickable();
                                        CharSequence cId = e.getViewIdResourceName();
                                        widgetSelect.widgetId = cId == null ? "" : cId.toString();
                                        CharSequence cDesc = e.getContentDescription();
                                        widgetSelect.widgetDescribe = cDesc == null ? "" : cDesc.toString();
                                        CharSequence cText = e.getText();
                                        widgetSelect.widgetText = cText == null ? "" : cText.toString();
                                        saveWidgetButton.setEnabled(true);
                                        pacName.setText(widgetSelect.appPackage);
                                        actName.setText(widgetSelect.appActivity);
                                        widget.setText("click:" + (e.isClickable() ? "true" : "false") + " " + "bonus:" + temRect.toShortString() + " " + "id:" + (cId == null ? "null" : cId.toString().substring(cId.toString().indexOf("id/") + 3)) + " " + "desc:" + (cDesc == null ? "null" : cDesc.toString()) + " " + "text:" + (cText == null ? "null" : cText.toString()));
                                        v.setBackgroundResource(R.drawable.node_focus);
                                    } else {
                                        v.setBackgroundResource(R.drawable.node);
                                    }
                                }
                            });
                            layoutParent.addView(img, params);
                        }
                        bParams.alpha = 0.5f;
                        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(viewLayoutAnalyze, bParams);
                        pacName.setText(widgetSelect.appPackage);
                        actName.setText(widgetSelect.appActivity);
                        button.setText("隐藏布局");
                    } else {
                        bParams.alpha = 0f;
                        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        windowManager.updateViewLayout(viewLayoutAnalyze, bParams);
                        saveWidgetButton.setEnabled(false);
                        button.setText("显示布局");
                    }
                }
            });
            switchAim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Button button = (Button) v;
                    if (cParams.alpha == 0) {
                        coordinateSelect.appPackage = currentPackage;
                        coordinateSelect.appActivity = currentActivity;
                        cParams.alpha = 0.5f;
                        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(viewClickPosition, cParams);
                        pacName.setText(coordinateSelect.appPackage);
                        actName.setText(coordinateSelect.appActivity);
                        button.setText("隐藏准心");
                    } else {
                        cParams.alpha = 0f;
                        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        windowManager.updateViewLayout(viewClickPosition, cParams);
                        savePositionButton.setEnabled(false);
                        button.setText("显示准心");
                    }
                }
            });
            saveWidgetButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Widget temWidget = new Widget(widgetSelect);
                    temWidget.createTime = System.currentTimeMillis();
                    dataDao.insertWidget(temWidget);
                    AppDescribe temAppDescribe = appDescribeMap.get(temWidget.appPackage);
                    if (temAppDescribe != null) {
                        temAppDescribe.getWidgetSetMapFromDatabase(dataDao);
                    }
                    saveWidgetButton.setEnabled(false);
                    pacName.setText(widgetSelect.appPackage + " (以下控件数据已保存)");
                }
            });
            savePositionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Coordinate temCoordinate = new Coordinate(coordinateSelect);
                    temCoordinate.createTime = System.currentTimeMillis();
                    dataDao.insertCoordinate(temCoordinate);
                    AppDescribe temAppDescribe = appDescribeMap.get(temCoordinate.appPackage);
                    if (temAppDescribe != null) {
                        temAppDescribe.getCoordinateMapFromDatabase(dataDao);
                    }
                    savePositionButton.setEnabled(false);
                    pacName.setText(coordinateSelect.appPackage + " (以下坐标数据已保存)");
                }
            });
            quitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    windowManager.removeViewImmediate(viewLayoutAnalyze);
                    windowManager.removeViewImmediate(viewDataShow);
                    windowManager.removeViewImmediate(viewClickPosition);
                    viewLayoutAnalyze = null;
                    viewDataShow = null;
                    viewClickPosition = null;
                    aParams = null;
                    bParams = null;
                    cParams = null;
                }
            });
            windowManager.addView(viewLayoutAnalyze, bParams);
            windowManager.addView(viewDataShow, aParams);
            windowManager.addView(viewClickPosition, cParams);
        } catch (Throwable e) {
//            e.printStackTrace();
        }
    }
}
