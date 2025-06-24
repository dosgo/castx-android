package com.dosgo.castx;

import android.content.Context;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import   castX.CastX;

class Control   {

    private static Context context;

    private static H264PlayerFragment play;

    private static boolean regJavaObj;


    public static  void cmd(String message) throws JSONException {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);
        
        JSONObject jsonObject = new JSONObject(message);

            MyAccessibilityService service = MyAccessibilityService.getInstance();
            String type = jsonObject.getString("type");
            if (service != null||type.equals("displayPower")) {

                if( type.equals("click")) {
                    try {
                        double x = jsonObject.getDouble("x");
                        double y = jsonObject.getDouble("y");
                        double duration = jsonObject.getDouble("duration");

                        double videoWidth = jsonObject.getDouble("videoWidth");
                        double videoHeight = jsonObject.getDouble("videoHeight");


                        Point  point=new Point(x,y);
                         GetRealPoint(metrics.widthPixels,metrics.heightPixels, (int) videoWidth, (int) videoHeight,point);



                        int phoneX= (int) point.x;
                        int phoneY= (int) point.y;
                        service.clickAtPoint(phoneX, phoneY, (long) duration);
                        System.out.println("click width:" + metrics.widthPixels + " x：" + phoneX + "y:" + phoneY);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                }

                if( type.equals("keyboard")) {
                    String code = jsonObject.getString("code");
                    System.out.println("keyboard code：" + code);
                    if(code.equals("home")){
                        service.simulateHomeKey();
                    }
                    if(code.equals("back")){
                        service.simulateBackKey();
                    }
                }

                if( type.equals("panstart")) {
                    double x = jsonObject.getDouble("x");
                    double y = jsonObject.getDouble("y");
                    double videoWidth = jsonObject.getDouble("videoWidth");
                    double videoHeight = jsonObject.getDouble("videoHeight");


                    Point  point=new Point(x,y);
                    GetRealPoint(metrics.widthPixels,metrics.heightPixels, (int) videoWidth, (int) videoHeight,point);

                    List<PointF> pointList = new ArrayList<>();
                        pointList.add(new PointF(
                                (float) point.x,
                                (float)point.y
                        ));


                    service.handleRawTouch(type, pointList);

                }

                if (type.equals("pan")){
                    double x = (int) jsonObject.getDouble("x");
                    double y = (int) jsonObject.getDouble("y");
                     if(x<0){
                        x=0;
                    }
                    if(y<0){
                        y=0;
                    }

                    double videoWidth = jsonObject.getDouble("videoWidth");
                    double videoHeight = jsonObject.getDouble("videoHeight");


                    Point  point=new Point(x,y);
                    GetRealPoint(metrics.widthPixels,metrics.heightPixels, (int) videoWidth, (int) videoHeight,point);
                    List<PointF> pointList = new ArrayList<>();
                    pointList.add(new PointF(
                            (float) point.x,
                            (float)point.y
                    ));

                    service.handleRawTouch(type, pointList);
                }
                if( type.equals("panend")) {
                    double x = (int) jsonObject.getDouble("x");
                    double y = (int) jsonObject.getDouble("y");
                   if(x<0){
                        x=0;
                    }
                    if(y<0){
                        y=0;
                    }

                    double videoWidth = jsonObject.getDouble("videoWidth");
                    double videoHeight = jsonObject.getDouble("videoHeight");


                    Point  point=new Point(x,y);
                    GetRealPoint(metrics.widthPixels,metrics.heightPixels, (int) videoWidth, (int) videoHeight,point);
                    List<PointF> pointList = new ArrayList<>();
                    pointList.add(new PointF(
                            (float) point.x,
                            (float)point.y
                    ));
                    service.handleRawTouch(type, pointList);
                }
                if(type.equals("displayPower")){
                    int action= (int) jsonObject.getDouble("action");
                    if(action==0) {
                        ScreenCastService.getInstance().setMinimumBrightness();
                    }else {
                        ScreenCastService.getInstance().restoreBrightness();
                    }
                }

            } else {
                Log.e("MainActivity", "无障碍服务未启用！");
            }

    }

    public static void GetRealPoint(int width,int heigt,double videoWidth,double videoHeight,Point point){
        double scaleWidth=  width/videoWidth;
        double scaleHeight= heigt/videoHeight;
        point.x= (int) (point.x*scaleWidth);
        if(point.x>width){
            point.x=width;
        }
        point.y= (int) (point.y*scaleHeight);
        if(point.y>heigt){
            point.y=heigt;
        }
        if(point.y<0){
            point.y=0;
        }
        if(point.x<0){
            point.x=0;
        }
    }
    public static class Point {
        public  double x;
        public  double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }


    public static void setContext(Context context) {
        Control.context = context;
        if(!regJavaObj) {
            regJavaObj = true;
            CastX.regJavaClass(new CallInterface());
        }

    }

    public static void unSetContext(){
        regJavaObj = false;
    }

    public static void setActivity(H264PlayerFragment _play) {
        Control.play = _play;
        if(!regJavaObj) {
            regJavaObj = true;
            CastX.regJavaClass(new CallInterface());
        }
    }


}

