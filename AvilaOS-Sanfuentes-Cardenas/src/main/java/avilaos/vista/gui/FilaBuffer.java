package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.so.PCB;
import avilaos.so.programas.ProgramaBuffer;
import avilaos.sync.EstadoBuffer;

/** Copia inmutable de un buffer para la tabla: contadores, valores de semáforos y bloqueados como texto. */
public record FilaBuffer(
        String nombre,
        int anfitrion,
        int elementos,
        int capacidad,
        int valorVacio,
        int valorLleno,
        int valorMutex,
        int producidos,
        int consumidos,
        String bloqueadosVacio,
        String bloqueadosLleno,
        String bloqueadosMutex,
        String usadoPor) {

    public static final String NINGUNO = "-";

    /** Convierte la foto del buffer en una fila lista para dibujar, sin la lista de procesos que lo usan. */
    public static FilaBuffer de(EstadoBuffer e) {
        return de(e, NINGUNO);
    }

    /** Convierte la foto del buffer en una fila; usadoPor lista los procesos vivos que trabajan con él. */
    public static FilaBuffer de(EstadoBuffer e, String usadoPor) {
        return new FilaBuffer(e.nombre(), e.anfitrion(), e.elementos(), e.capacidad(), e.valorVacio(),
                e.valorLleno(), e.valorMutex(), e.producidos(), e.consumidos(),
                etiquetas(e.bloqueadosVacio()), etiquetas(e.bloqueadosLleno()), etiquetas(e.bloqueadosMutex()), usadoPor);
    }

    /** Procesos vivos que usan el buffer, como "P3-H1 Sensor (PC0, local), P4-H1 Monitor (PC1, remoto)". */
    public static String usuarios(String buffer, int anfitrion, ListaEnlazada<PCB> procesos) {
        StringBuilder sb = new StringBuilder();
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null) {
            PCB p = nodo.getDato();
            if (p.getEstado() != EstadoProceso.TERMINADO && p.getPrograma() instanceof ProgramaBuffer programa
                    && programa.getBuffer().equals(buffer)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(p.getEtiqueta()).append(' ').append(p.getNombre()).append(" (PC").append(p.getComputador())
                        .append(p.getComputador() == anfitrion ? ", local)" : ", remoto)");
            }
            nodo = nodo.getSiguiente();
        }
        return sb.length() == 0 ? NINGUNO : sb.toString();
    }

    private static String etiquetas(ListaEnlazada<PCB> lista) {
        StringBuilder sb = new StringBuilder();
        Nodo<PCB> nodo = lista.getCabeza();
        while (nodo != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(nodo.getDato().getEtiqueta());
            nodo = nodo.getSiguiente();
        }
        return sb.length() == 0 ? NINGUNO : sb.toString();
    }
}
