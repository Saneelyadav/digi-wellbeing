package com.example.shortsblocker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
        
        // 1. Main Layout (Dark Background)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212")); // Dark mode background
        layout.setPadding(60, 60, 60, 60);

        // 2. "Saneel.AI" Logo
        TextView logo = new TextView(this);
        logo.setText("Saneel.AI");
        logo.setTextSize(32);
        logo.setTextColor(Color.parseColor("#4CAF50")); // Saneel Green
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, 0, 0, 20);

        // 3. Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("Smart Shorts Manager");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 100);

        // 4. Instructions
        TextView desc = new TextView(this);
        desc.setText("Status: 1 min Watch / 20 min Block\n\nTo start, click the button below and turn ON 'Shorts Blocker'.");
        desc.setTextSize(14);
        desc.setTextColor(Color.WHITE);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, 0, 0, 60);

        // 5. The Button
        Button btn = new Button(this);
        btn.setText("Activate Saneel.AI");
        btn.setBackgroundColor(Color.parseColor("#4CAF50"));
        btn.setTextColor(Color.WHITE);
        btn.setPadding(40, 20, 40, 20);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        // Add everything to screen
        layout.addView(logo);
        layout.addView(subtitle);
        layout.addView(desc);
        layout.addView(btn);
        
        setContentView(layout);
    }
}
}
