package br.com.logsproductionreview.mapper;

/**
 * Mensagem do tópico que não pode virar um log (JSON inválido, vazio ou com data ilegível).
 * Não adianta reprocessar: vai direto para a DLT.
 */
public class InvalidLogEventException extends RuntimeException {

    public InvalidLogEventException(String message) {
        super(message);
    }

    public InvalidLogEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
