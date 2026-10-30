package avilaos.vista.gui;

/** Quien recibe en el hilo de Swing la foto más reciente del estado. */
@FunctionalInterface
public interface ReceptorEstado<T> {

    /** Dibuja o guarda la foto recibida. */
    void recibir(T foto);
}
