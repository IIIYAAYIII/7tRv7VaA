package com.randompin.xposed;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.graphics.Color;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView textView = new TextView(this);
        textView.setText("RandomPIN 模块已安装。\n\n请在 LSPosed 管理器中：\n1. 启用本模块\n2. 勾选需要作用的应用 (如系统界面, Android 系统)\n3. 重启设备生效");
        textView.setTextSize(18);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextColor(Color.DKGRAY);
        textView.setGravity(Gravity.CENTER);
        
        setContentView(textView);
        
        Toast.makeText(this, "这是一枚 Xposed 模块，需 LSPosed 激活", Toast.LENGTH_LONG).show();
    }
}
