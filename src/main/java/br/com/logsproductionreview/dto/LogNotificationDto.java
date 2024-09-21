package br.com.logsproductionreview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogNotificationDto {

    private String action;
    private String message;
    private String  nameUser;
}
