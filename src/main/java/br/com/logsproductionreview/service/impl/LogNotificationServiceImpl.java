package br.com.logsproductionreview.service.impl;


import br.com.logsproductionreview.dto.LogNotificationDto;
import br.com.logsproductionreview.entity.LogNotification;
import br.com.logsproductionreview.repositories.LogNotificationRepository;
import br.com.logsproductionreview.service.LogNotificationService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class LogNotificationServiceImpl implements LogNotificationService {


    private final LogNotificationRepository logNotificationRepository;


    @Override
    public void saveLogNotification(LogNotificationDto logNotificationDto) {

     logNotificationRepository.save(
                LogNotification.builder()
                        .action(logNotificationDto.getAction())
                        .message(logNotificationDto.getMessage())
                        .nameUser(logNotificationDto.getNameUser())
                        .build()
     );

    }
}
