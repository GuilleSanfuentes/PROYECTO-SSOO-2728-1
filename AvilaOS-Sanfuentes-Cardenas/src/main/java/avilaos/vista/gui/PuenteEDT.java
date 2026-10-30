package avilaos.vista.gui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Pasa fotos del estado desde el hilo del reloj al hilo de Swing, conservando siempre la más reciente. */
public class PuenteEDT<T> {

    private final ReceptorEstado<T> destino;
    private final AtomicReference<T> pendiente = new AtomicReference<>();
    private final AtomicBoolean programado = new AtomicBoolean(false);

    /** Crea el puente; destino se ejecuta siempre en el hilo de Swing. */
    public PuenteEDT(ReceptorEstado<T> destino) {
        this.destino = destino;
    }

    /** El reloj deja la foto del estado en un buzón de una sola ranura; si había una sin leer, la reemplaza por la nueva. */
    public void publicar(T foto) {
        pendiente.set(foto);
        if (programado.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::entregar);
        }
    }

    /** La pantalla saca del buzón la foto más reciente y la dibuja. */
    private void entregar() {
        programado.set(false);
        T foto = pendiente.getAndSet(null);
        if (foto != null) {
            destino.recibir(foto);
        }
    }
}
