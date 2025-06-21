package com.dosgo.castx;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    // 静态变量保存当前活动的服务实例
    private static MyAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this; // 系统绑定服务时保存实例
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; // 服务销毁时清除引用
    }

    // 获取当前活动的实例（可能为 null）
    public static MyAccessibilityService getInstance() {
        return instance;
    }

    // 模拟返回键
    public void simulateBackKey() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    // 模拟 Home 键
    public void simulateHomeKey() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }




    /**
     * 模拟点击指定坐标
     * @param x 横坐标（单位：像素）
     * @param y 纵坐标（单位：像素）
     */
    public void clickAtPoint(int x, int y,long duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 1. 构建点击手势
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            try{
                path.moveTo(x, y);
            }catch (Exception e) {
                e.printStackTrace();
            }
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration)); // 点击持续50ms

            // 2. 执行手势
            dispatchGesture(builder.build(), null, null);
        } else {
            // 低版本 Android 需使用其他方法（如反射或辅助触控）
            Log.e("AccessibilityService", "Android 7.0 以下不支持直接坐标点击");
        }
    }


    private List<PointF> touchPoints = new ArrayList<>();
    private long touchStartTime;

    // 处理原始输入事件
    public void handleRawTouch(String type, List<PointF> points) {
        try{
            switch (type) {
                case "panstart":
                    touchPoints.clear();
                    touchPoints.addAll(points);
                    touchStartTime = System.currentTimeMillis();
                    break;

                case "pan":
                    touchPoints.addAll(points);
                    break;

                case "panend":
                    touchPoints.addAll(points);
                    recognizeGesture();
                    touchPoints.clear();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 核心手势识别逻辑
    private void recognizeGesture() {
        if (touchPoints.size() < 2) return;

        long duration = System.currentTimeMillis() - touchStartTime;

        // 滑动操作
        performSwipe(touchPoints, duration);

    }




    // 执行滑动
    private void performSwipe(List<PointF> points, long duration) {
        Path path = new Path();
        path.moveTo(points.get(0).x, points.get(0).y);

        for (int i = 1; i < points.size(); i++) {
            path.lineTo(points.get(i).x, points.get(i).y);
        }
        System.out.println("points size:"+ points.size());

        // 确保最小持续时间
       // long minDuration = Math.max(100, Math.min(500, points.size() * 10));

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        dispatchGesture(gesture, null, null);
    }


}