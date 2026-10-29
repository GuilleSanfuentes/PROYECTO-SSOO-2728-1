package avilaos.vista.gui;

import avilaos.cluster.Computador;
import avilaos.enums.ModoCPU;
import avilaos.enums.PoliticaTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.so.Kernel;
import avilaos.so.PCB;

/** Foto inmutable de un computador: CPU, RAM, política y colas, lista para dibujarse en el hilo de Swing. */
public record FotoComputador(
        String nombre,
        FilaProceso enCPU,
        ModoCPU modo,
        boolean ociosa,
        String ultimaAccion,
        int contadorCPU,
        int marCPU,
        int ramUsada,
        int ramCapacidad,
        PoliticaTipo politica,
        int quantum,
        FilaProceso[] nuevos,
        FilaProceso[] listos,
        FilaProceso[] bloqueados,
        FilaProceso[] terminados) {

    /** Toma la foto del computador en este instante. */
    public static FotoComputador de(Computador computador) {
        Kernel kernel = computador.getKernel();
        CPU cpu = computador.getCpu();
        PCB enEjecucion = kernel.getEnEjecucion();
        return new FotoComputador(computador.getNombre(),
                enEjecucion == null ? null : FilaProceso.de(enEjecucion),
                cpu.getModo(), cpu.isOciosa(), cpu.getUltimaAccion(), cpu.getContador(), cpu.getMar(),
                computador.getRam().getUsada(), computador.getRam().getCapacidad(),
                kernel.getPolitica(), kernel.getQuantum(),
                filas(kernel.getNuevos()), filas(kernel.getListos()),
                filas(kernel.getBloqueados()), filas(kernel.getTerminados()));
    }

    private static FilaProceso[] filas(ListaEnlazada<PCB> lista) {
        FilaProceso[] filas = new FilaProceso[lista.tamano()];
        Nodo<PCB> nodo = lista.getCabeza();
        for (int i = 0; i < filas.length && nodo != null; i++) {
            filas[i] = FilaProceso.de(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return filas;
    }
}
