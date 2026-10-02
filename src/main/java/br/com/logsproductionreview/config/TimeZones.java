package br.com.logsproductionreview.config;

import java.time.ZoneId;

public final class TimeZones {

    /** Fuso usado para interpretar datas sem offset e para agrupar o resumo por dia. */
    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private TimeZones() {
    }
}
