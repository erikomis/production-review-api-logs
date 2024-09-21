package br.com.logsproductionreview.message.consumer;


import br.com.logsproductionreview.dto.LogNotificationDto;
import br.com.logsproductionreview.service.LogNotificationService;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class ProductionReviewApiConsumer {


    private final LogNotificationService logNotificationService;

    @KafkaListener(topics = "production-review-api", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message) {
        Gson gson = new Gson();
        LogNotificationDto dto = gson.fromJson(message, LogNotificationDto.class);

        logNotificationService.saveLogNotification(dto);
    }
}
