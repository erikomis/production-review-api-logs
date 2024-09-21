package br.com.logsproductionreview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "log_notification")
public class LogNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String action;
    private String message;
    private String  nameUser;



}
