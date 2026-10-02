package br.com.logsproductionreview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

// VIA_DTO: páginas saem como {content, page:{size,number,totalElements,totalPages}}, igual à API principal
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class LogsProductionReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogsProductionReviewApplication.class, args);
    }

}
