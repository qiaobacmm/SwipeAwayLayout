package com.bulletnoid.android.widget.SwipeAwayLayout;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import com.bulletnoid.android.widget.SwipeAwayLayout.lib.SwipeAwayLayout;

public class MainActivity extends Activity {
    SwipeAwayLayout view_root;
    RadioGroup radig_mode;
    Button btn_new_ac;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        view_root = (SwipeAwayLayout) findViewById(R.id.view_root);
        radig_mode = (RadioGroup) findViewById(R.id.radig_mode);
        btn_new_ac = (Button) findViewById(R.id.btn_new_ac);

        radig_mode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.radi_mode_left:
                        view_root.setSwipeOrientation(SwipeAwayLayout.LEFT_ONLY);
                        break;
                    case R.id.radi_mode_right:
                        view_root.setSwipeOrientation(SwipeAwayLayout.RIGHT_ONLY);
                        break;
                    case R.id.radi_mode_both:
                        view_root.setSwipeOrientation(SwipeAwayLayout.LEFT_RIGHT);
                        break;
                    default:
                }
            }
        });

        btn_new_ac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, MainActivity.class));
            }
        });

        view_root.setOnSwipeAwayListener(new SwipeAwayLayout.OnSwipeAwayListener() {
            @Override
            public void onSwipedAway() {
                finish();
                overridePendingTransition(0, 0);
            }
        });
    }
}
