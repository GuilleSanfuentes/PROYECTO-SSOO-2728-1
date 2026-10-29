package avilaos.vista.gui;

import avilaos.cluster.Cluster;

/** Foto inmutable del clúster en un ciclo: el número de ciclo y la foto de cada computador. */
public record FotoCluster(long ciclo, FotoComputador[] computadores) {

    /** Toma la foto del clúster en este instante. */
    public static FotoCluster de(Cluster cluster) {
        FotoComputador[] fotos = new FotoComputador[cluster.cantidadComputadores()];
        for (int i = 0; i < fotos.length; i++) {
            fotos[i] = FotoComputador.de(cluster.getComputador(i));
        }
        return new FotoCluster(cluster.getReloj().getCiclo(), fotos);
    }
}
