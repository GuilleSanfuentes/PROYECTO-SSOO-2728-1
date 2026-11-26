package avilaos.cluster;

import avilaos.enums.PoliticaDiscoTipo;
import avilaos.metricas.MetricasDisco;

/** Resultado de simular la carga con una política; completo es false si se alcanzó el límite de ciclos. */
public record ResultadoPolitica(PoliticaDiscoTipo politica, Atencion[] orden, MetricasDisco metricas, int procesos,
        int vencidos, int rechazados, boolean completo, long cicloFinal) {

    public ResultadoPolitica {
        orden = orden.clone();
    }

    @Override
    public Atencion[] orden() {
        return orden.clone();
    }

    /** Primeros bloques en el orden en que se atendieron: "95 180 34". */
    public String bloquesEnOrden() {
        StringBuilder sb = new StringBuilder();
        for (Atencion a : orden) {
            sb.append(sb.length() > 0 ? " " : "").append(a.bloque());
        }
        return sb.toString();
    }

    /** Nombre para la leyenda: avisa si la corrida quedó incompleta o dejó procesos sin cumplir. */
    public String etiqueta() {
        StringBuilder sb = new StringBuilder(politica.getNombre());
        if (!completo) {
            sb.append(" (incompleta)");
        }
        if (vencidos > 0 || rechazados > 0) {
            sb.append(" (").append(vencidos).append(" vencidos, ").append(rechazados).append(" rechazados)");
        }
        return sb.toString();
    }
}
