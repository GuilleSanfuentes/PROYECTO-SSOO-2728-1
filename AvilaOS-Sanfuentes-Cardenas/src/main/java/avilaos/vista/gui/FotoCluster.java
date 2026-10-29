package avilaos.vista.gui;

import avilaos.cluster.Cluster;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.sync.Buffer;

/** Foto inmutable del clúster en un ciclo: computadores, buffers y las últimas líneas del log y de la consola. */
public record FotoCluster(long ciclo, FotoComputador[] computadores, FilaBuffer[] buffers, String[] log,
        String[] consola) {

    public static final int LINEAS_LOG = 500;
    public static final int LINEAS_CONSOLA = 1000;

    /** Toma la foto del clúster en este instante. */
    public static FotoCluster de(Cluster cluster) {
        FotoComputador[] fotos = new FotoComputador[cluster.cantidadComputadores()];
        for (int i = 0; i < fotos.length; i++) {
            fotos[i] = FotoComputador.de(cluster.getComputador(i));
        }
        ListaEnlazada<Buffer> lista = cluster.getBuffers().copiar();
        FilaBuffer[] buffers = new FilaBuffer[lista.tamano()];
        Nodo<Buffer> nodo = lista.getCabeza();
        for (int i = 0; i < buffers.length && nodo != null; i++) {
            buffers[i] = FilaBuffer.de(nodo.getDato().estado());
            nodo = nodo.getSiguiente();
        }
        return new FotoCluster(cluster.getReloj().getCiclo(), fotos, buffers,
                cluster.getLog().copiarUltimos(LINEAS_LOG), Debug.getConsola().copiarUltimos(LINEAS_CONSOLA));
    }
}
