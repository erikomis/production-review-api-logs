package br.com.logsproductionreview.service;

import br.com.logsproductionreview.entity.LogNotification;

public interface LogNotificationService {

    /**
     * Grava o log. Um {@code eventId} já gravado é ignorado sem erro.
     *
     * @return {@code true} se gravou, {@code false} se era duplicado
     */
    boolean saveLogNotification(LogNotification logNotification);
}
