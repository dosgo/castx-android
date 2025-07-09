package com.dosgo.castx;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


public class UsbItemAdapter extends ArrayAdapter<UsbItemAdapter.UsbItem> {

    public static class UsbItem {
        private String name;
        private UsbDevice device;


        public UsbItem(String name,UsbDevice  device) {
            this.name = name;
            this.device=device;
        }

        public String getName() {
            return name;
        }

        public UsbDevice getDevice() {
            return device;
        }
    }
    private final List<UsbItem> items;

    public UsbItemAdapter(Context context, List<UsbItem> items) {
        super(context, 0);
        this.items = items;
    }

    @Override
    public UsbItem getItem(int position) {
        return items.get(position); // 正确返回UsbItem对象
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // 获取当前项的数据
        UsbItem item = getItem(position);
        
        // 检查是否已有视图可复用
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.usb_item, parent, false);
        }
        
        // 查找视图中的组件
        TextView titleView = convertView.findViewById(R.id.item_title);
        Button button = convertView.findViewById(R.id.item_button);


        // 设置标题文本
        titleView.setText(item.getDevice().getProductName());
        
        // 设置按钮点击事件
        button.setOnClickListener(v -> {
            // 显示点击了哪个项目的按钮
            Toast.makeText(getContext(), "点击了: " + item.getDevice().getProductName(), Toast.LENGTH_SHORT).show();
        });
        return convertView;
    }
}