package com.companion.mobile;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

public final class WidgetRestorer {
    private WidgetRestorer() {}

    private static final Class<?>[] PROVIDERS = {
        CompanionWidget1x1.class, CompanionWidget2x2.class, CompanionWidget3x2.class,
        CompanionWidget4x1.class, CompanionWidget1x4.class, CompanionWidget4x2.class,
        CompanionWidget4x3.class, CompanionWidget4x4.class, CompanionWidgetRotary.class,
        CompanionWidgetMixed4x4.class, CompanionWidgetRotary4x1.class
    };

    public static void restoreAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        for (Class<?> provider : PROVIDERS) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, provider));
            for (int id : ids) {
                // Keep the saved host, shape and Companion surface identity intact.
                if (!WidgetPrefs.host(app, id).isEmpty()) WidgetSatelliteService.start(app, id);
            }
        }
    }
}
