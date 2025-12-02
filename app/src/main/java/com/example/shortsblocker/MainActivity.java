package com.example.shortsblocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        TextView text = new TextView(this);
        text.setText("1. Click button below\n2. Turn ON 'Shorts Blocker'");
        text.setGravity(Gravity.CENTER);
        text.setPadding(0,0,0,50);
        
        Button btn = new Button(this);
        btn.setText("Enable Service");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        layout.addView(text);
        layout.addView(btn);
        setContentView(layout);
    }
}
