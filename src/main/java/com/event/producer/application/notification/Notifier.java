package com.event.producer.application.notification;

import com.event.producer.domain.notification.Notification;

/**
 * Outbound port: how a notification reaches the user. The implementation lives in {@code adapter.notification}.
 * <p>
 * Today it only writes a log line. In a real system it would send an e-mail or a push notification, which can fail,
 * so a caller must let the exception go and not assume the user was told.
 */
public interface Notifier {

    void send(Notification notification);
}
