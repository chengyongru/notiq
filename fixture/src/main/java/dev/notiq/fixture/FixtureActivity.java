package dev.notiq.fixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/** Separate notification source for device smoke tests; not included in the Notiq APK. */
public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel immediate = new NotificationChannel("instant_probe", "测试通知", NotificationManager.IMPORTANCE_HIGH);
        immediate.enableVibration(true);
        manager.createNotificationChannel(immediate);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 100, 32, 32);
        String[][] samples = {{"促销通知", "限时优惠", "今日大促，领满减券，立即抢购！"}, {"物流通知", "订单已送达", "您购买的商品已送达，请及时取件。"}};
        for (int i = 0; i < samples.length; i++) {
            final int id = i + 1;
            final String[] sample = samples[i];
            Button button = new Button(this);
            button.setText(sample[0]);
            button.setOnClickListener(v -> post(manager, id, sample[1], sample[2]));
            layout.addView(button);
        }
        setContentView(layout);
        if (getIntent().getBooleanExtra("openedFromReminder", false)) {
            Button opened = new Button(this);
            opened.setText("已打开原通知页面");
            layout.addView(opened);
        }
        String kind = getIntent().getStringExtra("kind");
        if (kind != null) {
            boolean ad = kind.equals("ad");
            new android.os.Handler(getMainLooper()).postDelayed(() -> post(manager, getIntent().getIntExtra("notificationId", ad ? 1 : 2), kind.equals("timeout") ? "超时测试" : ad ? samples[0][1] : samples[1][1], ad ? samples[0][2] : samples[1][2]), getIntent().getIntExtra("delayMs", 0));
        }
    }
    private void post(NotificationManager manager, int id, String title, String body) {
        Intent target = new Intent(this, FixtureActivity.class).putExtra("openedFromReminder", true);
        PendingIntent open = PendingIntent.getActivity(this, id, target, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "instant_probe").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setContentIntent(open).setAutoCancel(true).build();
        manager.notify(id, notification);
        if (getIntent().getBooleanExtra("repeat", false)) {
            layoutRepeat(manager, id, notification);
        }
    }
    private void layoutRepeat(NotificationManager manager, int id, Notification notification) {
        new android.os.Handler(getMainLooper()).postDelayed(() -> manager.notify(id, notification), 2000);
    }
}
