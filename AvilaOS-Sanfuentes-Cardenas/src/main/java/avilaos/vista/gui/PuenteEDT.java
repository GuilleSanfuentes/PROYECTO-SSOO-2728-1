package avilaos.vista.gui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Pasa fotos del estado desde el hilo del reloj al hilo de Swing, conservando siempre la más reciente. */
public class PuenteEDT<T> {

    private final Consumer<T> destino;
    private final AtomicReference<T> pendiente = new AtomicReference<>();
    private final AtomicBoolean programado = new AtomicBoolean(false);

    /** Crea el puente; destino se ejecuta siempre en el hilo de Swing. */
    public PuenteEDT(Consumer<T> destino) {
        this.destino = destino;
    }

    public void publicar(T foto) {
        pendiente.set(foto);
        if (programado.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::entregar);
        }
    }

    private void entregar() {
        programado.set(false);
        T foto = pendiente.getAndSet(null);
        if (foto != null) {
            destino.accept(foto);
        }
    }
}
