package br.com.logsproductionreview.repositories;

import br.com.logsproductionreview.entity.LogNotification;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface LogNotificationRepository extends MongoRepository<LogNotification, String> {

}
