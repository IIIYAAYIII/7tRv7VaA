package com.randompin.xposed;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.graphics.Color;

public class SettingsActivity extends Activity {
    
    // 这个方法永远返回 false，除非被 Xposed 成功 Hook 强制返回 true
    public boolean isModuleActive() {
        return false;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        boolean isActive = isModuleActive();
        
        TextView textView = new TextView(this);
        if (isActive) {
            textView.setText("✅ RandomPIN 模块已成功激活！\n\n您现在的系统界面 (System UI) 已被接管。\n\n功能：\n1. 锁屏密码乱序\n2. 桌面双击锁屏");
            textView.setTextColor(Color.parseColor("#388E3C")); // Green
        } else {
            textView.setText("❌ 模块未激活\n\n请在 LSPosed 管理器中：\n1. 启用本模块\n2. 勾选作用域:\n   - 系统界面 (System UI)\n   - Android Framework\n3. 重启设备生效");
            textView.setTextColor(Color.parseColor("#D32F2F")); // Red
        }
        
        textView.setTextSize(18);
        textView.setPadding(60, 60, 60, 60);
        textView.setGravity(Gravity.CENTER);
        
        setContentView(textView);
        
        Toast.makeText(this, isActive ? "模块运行正常" : "请先在 LSPosed 中激活", Toast.LENGTH_SHORT).show();
    }
}
