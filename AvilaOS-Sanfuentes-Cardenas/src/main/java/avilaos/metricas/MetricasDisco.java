package avilaos.metricas;

import avilaos.enums.PoliticaDiscoTipo;

/** Foto de las métricas del disco y de la suspensión, tomada de una vez; no cambia después. */
public record MetricasDisco(long ciclo, PoliticaDiscoTipo politicaActiva, long desplazamientoTotal, long atendidas,
        long[] atendidasPorPolitica, long solicitudesMedidas, double esperaPromedioTotal,
        double esperaPromedioSinAnticipacion, long suspensiones, long reactivaciones, PuntoDesplazamiento[] serie,
        CambioPolitica[] cambios) {

    public MetricasDisco {
        atendidasPorPolitica = atendidasPorPolitica.clone();
        serie = serie.clone();
        cambios = cambios.clone();
    }

    /** Solicitudes terminadas que eligió la política indicada. */
    public long atendidasPor(PoliticaDiscoTipo tipo) {
        return tipo == null ? 0 : atendidasPorPolitica[tipo.ordinal()];
    }

    @Override
    public long[] atendidasPorPolitica() {
        return atendidasPorPolitica.clone();
    }

    @Override
    public PuntoDesplazamiento[] serie() {
        return serie.clone();
    }

    @Override
    public CambioPolitica[] cambios() {
        return cambios.clone();
    }
}
