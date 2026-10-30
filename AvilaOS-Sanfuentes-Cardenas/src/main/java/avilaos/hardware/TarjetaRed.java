package avilaos.hardware;

import avilaos.enums.TipoInterrupcion;

/** Tarjeta de red: cuenta la latencia de cada acceso remoto y avisa a la CPU al terminar. */
public class TarjetaRed extends ControladorEspera {

    /** Crea la tarjeta conectada a la línea de interrupciones de su computador. */
    public TarjetaRed(String nombre, LineaInterrupciones linea) {
        super(nombre, TipoInterrupcion.FIN_RED, linea);
    }
}
