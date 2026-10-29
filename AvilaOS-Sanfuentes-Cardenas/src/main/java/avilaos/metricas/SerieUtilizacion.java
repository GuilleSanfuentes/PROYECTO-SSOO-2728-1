package avilaos.metricas;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;

/** Serie de utilización de la CPU de un computador. No es segura entre hilos: la sincroniza RegistroUtilizacion. */
class SerieUtilizacion {

    private final String nombre;
    private final int ventana;
    private final long[] acumulados;
    private final ListaEnlazada<MuestraUtilizacion> muestras = new ListaEnlazada<>();
    private int cantidad;

    /** Crea la serie; ocupadosIniciales son los ciclos ocupados de la CPU al momento de empezar a medir. */
    SerieUtilizacion(String nombre, int ventana, long ocupadosIniciales) {
        this.nombre = nombre;
        this.ventana = ventana;
        this.acumulados = new long[ventana + 1];
        this.acumulados[0] = ocupadosIniciales;
    }

    /** Mide la ocupación de la CPU: compara los ciclos ocupados con los de hace 20 ciclos, divide la diferencia entre 20 (o entre los que lleve al inicio) y guarda ese porcentaje como un punto del gráfico. */
    void agregar(long ciclo, long ocupados) {
        cantidad++;
        int anchoVentana = Math.min(cantidad, ventana);
        long antes = acumulados[(cantidad - anchoVentana) % acumulados.length];
        acumulados[cantidad % acumulados.length] = ocupados;
        muestras.agregarFinal(new MuestraUtilizacion(ciclo, 100.0 * (ocupados - antes) / anchoVentana));
    }

    /** Copia de las muestras en orden de ciclo. */
    ListaEnlazada<MuestraUtilizacion> copiar() {
        ListaEnlazada<MuestraUtilizacion> copia = new ListaEnlazada<>();
        Nodo<MuestraUtilizacion> nodo = muestras.getCabeza();
        while (nodo != null) {
            copia.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return copia;
    }

    String getNombre() {
        return nombre;
    }
}
