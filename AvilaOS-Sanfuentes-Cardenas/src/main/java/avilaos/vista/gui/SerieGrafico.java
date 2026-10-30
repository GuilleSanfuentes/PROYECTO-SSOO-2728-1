package avilaos.vista.gui;

/** Puntos de utilización de CPU de un computador (ciclo y porcentaje), copiados para el gráfico. */
public record SerieGrafico(String nombre, double[] ciclos, double[] porcentajes) {
}
