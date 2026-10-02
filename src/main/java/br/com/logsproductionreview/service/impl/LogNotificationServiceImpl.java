package br.com.logsproductionreview.service.impl;

import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.repositories.LogNotificationRepository;
import br.com.logsproductionreview.service.LogNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogNotificationServiceImpl implements LogNotificationService {

    private final LogNotificationRepository logNotificationRepository;

    @Override
    public boolean saveLogNotification(LogNotification logNotification) {
        try {
            // insert (e não save) para o índice único de eventId barrar a reentrega
            logNotificationRepository.insert(logNotification);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Evento {} já registrado; ignorando duplicata", logNotification.getEventId());
            return false;
        }
    }
}
