
package org.zarroboogs.weibo.service;

import org.zarroboogs.util.net.WeiboException;
import org.zarroboogs.utils.AppLoggerUtils;
import org.zarroboogs.weibo.BeeboApplication;
import org.zarroboogs.weibo.R;
import org.zarroboogs.weibo.bean.AccountBean;
import org.zarroboogs.weibo.bean.UnreadBean;
import org.zarroboogs.weibo.bean.UnreadTabIndex;
import org.zarroboogs.weibo.dao.ClearUnreadDao;
import org.zarroboogs.weibo.support.lib.RecordOperationAppBroadcastReceiver;
import org.zarroboogs.weibo.support.utils.BundleArgsConstants;
import org.zarroboogs.weibo.support.utils.Utility;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.HashMap;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class SimpleTextNotificationService extends NotificationServiceHelper {

    private class ValueWrapper {

        private AccountBean accountBean;

        private UnreadBean unreadBean;

        private Intent clickToOpenAppPendingIntentInner;

        private String ticker;

        private RecordOperationAppBroadcastReceiver clearNotificationEventReceiver;
    }

    // key is account uid
    private static HashMap<String, ValueWrapper> valueBagHashMap = new HashMap<String, ValueWrapper>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        ValueWrapper valueWrapper = new ValueWrapper();

        valueWrapper.accountBean = intent.getParcelableExtra(NotificationServiceHelper.ACCOUNT_ARG);

        valueWrapper.unreadBean = intent.getParcelableExtra(NotificationServiceHelper.UNREAD_ARG);

        valueWrapper.clickToOpenAppPendingIntentInner = intent
                .getParcelableExtra(NotificationServiceHelper.PENDING_INTENT_INNER_ARG);
        valueWrapper.ticker = intent.getStringExtra(NotificationServiceHelper.TICKER);

        valueBagHashMap.put(valueWrapper.accountBean.getUid(), valueWrapper);

        AppLoggerUtils.i("service account name=" + valueWrapper.accountBean.getUsernick());

        buildNotification(valueWrapper.accountBean.getUid());

        stopSelf();

        return super.onStartCommand(intent, flags, startId);
    }

    private void buildNotification(String uid) {

        ValueWrapper valueWrapper = valueBagHashMap.get(uid);

        if (valueWrapper == null) {
            return;
        }

        final AccountBean accountBean = valueWrapper.accountBean;
        final UnreadBean unreadBean = valueWrapper.unreadBean;

        Intent clickToOpenAppPendingIntentInner = valueWrapper.clickToOpenAppPendingIntentInner;

        String ticker = valueWrapper.ticker;

        final RecordOperationAppBroadcastReceiver clearNotificationEventReceiver = valueWrapper.clearNotificationEventReceiver;

        Notification.Builder builder = new Notification.Builder(getBaseContext()).setTicker(ticker)
                .setContentText(accountBean.getUsernick())
                .setSmallIcon(R.drawable.ic_notification).setAutoCancel(true)
                .setContentIntent(getPendingIntent(clickToOpenAppPendingIntentInner))
                .setOnlyAlertOnce(true);

        builder.setContentTitle(ticker);

        Utility.unregisterReceiverIgnoredReceiverNotRegisteredException(BeeboApplication.getInstance(),
                clearNotificationEventReceiver);

        valueWrapper.clearNotificationEventReceiver = new RecordOperationAppBroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            new ClearUnreadDao(accountBean.getAccess_token()).clearMentionStatusUnread(unreadBean,
                                    accountBean.getUid());
                            new ClearUnreadDao(accountBean.getAccess_token()).clearMentionCommentUnread(unreadBean,
                                    accountBean.getUid());
                            new ClearUnreadDao(accountBean.getAccess_token()).clearCommentUnread(unreadBean,
                                    accountBean.getUid());
                        } catch (WeiboException ignored) {

                        } finally {
                            Utility.unregisterReceiverIgnoredReceiverNotRegisteredException(BeeboApplication.getInstance(),
                                    clearNotificationEventReceiver);
                            if (Utility.isDebugMode()) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "iBeebo的通知被移除",
                                                Toast.LENGTH_SHORT).show();

                                    }
                                });
                            }
                        }

                    }
                }).start();
            }
        };

        IntentFilter intentFilter = new IntentFilter(RESET_UNREAD_MENTIONS_WEIBO_ACTION);

        BeeboApplication.getInstance().registerReceiver(valueWrapper.clearNotificationEventReceiver, intentFilter);

        Intent broadcastIntent = new Intent(RESET_UNREAD_MENTIONS_WEIBO_ACTION);

        PendingIntent deletedPendingIntent = PendingIntent.getBroadcast(BeeboApplication.getInstance(), accountBean.getUid()
                        .hashCode(), broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setDeleteIntent(deletedPendingIntent);

        Utility.configVibrateLedRingTone(builder);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(
                NOTIFICATION_SERVICE);
        notificationManager.notify(getMentionsWeiboNotificationId(accountBean), builder.build());
    }

    private PendingIntent getPendingIntent(Intent clickToOpenAppPendingIntentInner) {
        clickToOpenAppPendingIntentInner.setExtrasClassLoader(getClass().getClassLoader());
        clickToOpenAppPendingIntentInner.putExtra(BundleArgsConstants.OPEN_NAVIGATION_INDEX_EXTRA,
                UnreadTabIndex.MENTION_WEIBO);
        PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0, clickToOpenAppPendingIntentInner,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

}
