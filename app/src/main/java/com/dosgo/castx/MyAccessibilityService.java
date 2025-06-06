package com.dosgo.castx;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

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


    // 左滑手势（从右向左滑动）
    public void performSwipeRight(int screenWidth,int screenHeight) {

        // 坐标参数（单位：像素）
        float startX = screenWidth * 0.9f; // 起始点：屏幕右侧90%位置
        float startY = screenHeight / 2f;  // Y轴居中
        float endX = screenWidth * 0.1f;   // 终点：屏幕左侧10%位置
        int duration = 300;                // 滑动持续时间（毫秒）

        // 构建路径
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, startY); // 横向直线滑动

        // 创建手势
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        // 执行手势
        dispatchGesture(gesture, null, null);
    }

    // 右滑手势（从左向右滑动）
    public void performSwipeLeft(int screenWidth,int screenHeight) {


        float startX = screenWidth * 0.1f; // 起始点：屏幕左侧10%位置
        float startY = screenHeight / 2f;
        float endX = screenWidth * 0.9f;   // 终点：屏幕右侧90%位置
        int duration = 300;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, startY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        dispatchGesture(gesture, null, null);
    }

    // 上滑（从下往上）
    public void performSwipeUp(int screenWidth, int screenHeight) {
        float startX = screenWidth / 2f;
        float startY = screenHeight * 0.8f;
        float endY = screenHeight * 0.5f;  // 仅滑动10%的屏幕高度
        int duration = 400;               // 增加持续时间到400ms

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(startX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        dispatchGesture(gesture, null, null);
    }

    // 下滑（从上往下，滚动一屏的1/4距离）
    public void performSwipeDown(int screenWidth, int screenHeight) {
        float startX = screenWidth / 2f;
        float startY = screenHeight * 0.2f;
        float endY = screenHeight * 0.5f;  // 仅滑动10%的屏幕高度
        int duration = 400;               // 增加持续时间到400ms

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(startX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        dispatchGesture(gesture, null, null);
    }


    /**
     * 模拟点击指定坐标
     * @param x 横坐标（单位：像素）
     * @param y 纵坐标（单位：像素）
     */
    public void clickAtPoint(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 1. 构建点击手势
            GestureDescription.Builder builder = new GestureDescription.Builder();
            Path path = new Path();
            path.moveTo(x, y);
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50)); // 点击持续50ms

            // 2. 执行手势
            dispatchGesture(builder.build(), null, null);
        } else {
            // 低版本 Android 需使用其他方法（如反射或辅助触控）
            Log.e("AccessibilityService", "Android 7.0 以下不支持直接坐标点击");
        }
    }

    public void performSwipe(int startX, int startY,int endX,int endY,int duration) {


        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        dispatchGesture(gesture, null, null);
    }

}