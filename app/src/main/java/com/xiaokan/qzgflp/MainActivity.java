package com.xiaokan.qzgflp;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private static final int PINK = 0xFFFF8FB1;
    private static final int PINK_DEEP = 0xFFD65582;
    private static final int LILAC = 0xFF7B61FF;
    private static final int TEXT_MAIN = 0xFF5D4157;
    private static final int TEXT_SOFT = 0xFF9E8090;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.bg_main);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        page.setPadding(pad, dp(28), pad, pad);
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int inner = dp(20);
        card.setPadding(inner, dp(26), inner, inner);
        page.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setContentDescription(getString(R.string.app_name));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(88), dp(88));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(icon, iconLp);

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextSize(22);
        title.setTextColor(PINK_DEEP);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, 0);
        card.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("1.1.0 · com.xiaokan.qzgflp · libxposed API 102");
        subtitle.setTextSize(12);
        subtitle.setTextColor(TEXT_SOFT);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, 0);
        card.addView(subtitle, matchWrap());

        card.addView(divider(), matchWrap());

        addSection(card, "使用说明");
        addText(card, "① 在 LSPosed 中启用本模块，作用域勾选「系统框架（android）」，可再加「系统界面」「Oplus 截屏」「Oplus 应用平台」等截图组件，然后重启手机。", 15, TEXT_MAIN);
        addText(card, "② 解除系统截图与屏幕录像的内容限制：FLAG_SECURE 安全窗口、SurfaceView 安全层、安全层黑屏替换、录屏/截图监听回调全部放行，涉及应用都能正常截到完整画面。", 15, TEXT_MAIN);
        addText(card, "③ 手机需要已 Root 并装有支持 libxposed 新 API 的 LSPosed；ColorOS 16 已适配，同时兼容 HyperOS / One UI / 原生等第三方 root 机型。", 15, TEXT_MAIN);

        addSection(card, "适配范围");
        addText(card, "Android 9（API 28）起 · framework 层解除，与应用自身实现无关", 15, LILAC);
        addText(card, "内置 HyperOS（小米）/ One UI（三星）/ ColorOS（Oplus 长截屏）私有限制解除", 15, LILAC);

        addSection(card, "小提示");
        addText(card, "模块在 system_server 内全量生效；在 LSPosed 停用模块并重启即可完全恢复系统默认行为。", 15, TEXT_SOFT);

        TextView footer = new TextView(this);
        footer.setText("ForceCapture · 让每一帧画面都愿意被记住");
        footer.setTextSize(12);
        footer.setTextColor(PINK);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(22), 0, 0);
        card.addView(footer, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private View divider() {
        View bar = new View(this);
        bar.setBackgroundResource(R.drawable.bg_divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(3));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(0, dp(16), 0, dp(4));
        wrapper.addView(bar, lp);
        return wrapper;
    }

    private void addSection(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(PINK_DEEP);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart, 0, 0, 0);
        view.setCompoundDrawablePadding(dp(6));
        view.setPadding(0, dp(18), 0, dp(2));
        parent.addView(view, matchWrap());
    }

    private void addText(LinearLayout parent, String text, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1.1f);
        view.setPadding(0, dp(8), 0, 0);
        parent.addView(view, matchWrap());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
