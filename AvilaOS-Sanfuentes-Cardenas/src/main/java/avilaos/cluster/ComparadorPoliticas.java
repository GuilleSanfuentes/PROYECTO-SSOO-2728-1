package avilaos.cluster;

import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.PCB;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;

/** Simula sin interfaz la carga de los dos JSON con cada política de disco, partiendo de su estado inicial. */
public final class ComparadorPoliticas {

    /** Resultados en el orden del enum, o el motivo por el que no se pudo comparar. */
    public record Comparacion(String motivo, ResultadoPolitica[] resultados) {

        public Comparacion {
            resultados = resultados.clone();
        }

        @Override
        public ResultadoPolitica[] resultados() {
            return resultados.clone();
        }

        /** Indica si la comparación se hizo. */
        public boolean hecha() {
            return motivo == null;
        }

        /** Resultado de la política indicada, o null si no está. */
        public ResultadoPolitica de(PoliticaDiscoTipo politica) {
            for (ResultadoPolitica r : resultados) {
                if (r.politica() == politica) {
                    return r;
                }
            }
            return null;
        }
    }

    public static final long MAX_CICLOS = 200_000;

    private static final String ORIGEN = "Comparador";
    private static final int EVENTOS_POR_CORRIDA = 200;

    private ComparadorPoliticas() {
    }

    /** Compara las seis políticas; abierta es la simulación en pantalla (o null), donde se anota qué números se usaron. */
    public static Comparacion comparar(CargaSimulacion carga, Cluster abierta) {
        return comparar(carga, abierta, MAX_CICLOS);
    }

    /** Igual, con otro límite de ciclos por política. Tarda: la interfaz debe llamarlo fuera de su hilo. */
    public static Comparacion comparar(CargaSimulacion carga, Cluster abierta, long maxCiclos) {
        if (carga == null || maxCiclos < 1) {
            Debug.error(ORIGEN, "falta la carga o el límite de ciclos no es válido (" + maxCiclos + ")");
            return new Comparacion("Datos de comparación no válidos", new ResultadoPolitica[0]);
        }
        String motivo = carga.motivoNoArmar();
        if (motivo != null) {
            Debug.info(ORIGEN, "no se compara: " + motivo);
            return new Comparacion(motivo, new ResultadoPolitica[0]);
        }
        DatosSistemaArchivos sistema = carga.getSistemaArchivos();
        DatosProcesos procesos = carga.getProcesos();
        PoliticaDiscoTipo[] politicas = PoliticaDiscoTipo.values();
        ResultadoPolitica[] resultados = new ResultadoPolitica[politicas.length];
        Numeros numeros = new Numeros();
        boolean antes = Debug.isSegundoPlano();
        Debug.setSegundoPlano(true);
        try {
            for (int i = 0; i < politicas.length; i++) {
                resultados[i] = simular(sistema, procesos.paraComparar(politicas[i]), maxCiclos, numeros);
            }
        } finally {
            Debug.setSegundoPlano(antes);
        }
        anotarNumeros(abierta, numeros);
        Debug.ok(ORIGEN, "comparadas " + politicas.length + " políticas de disco");
        return new Comparacion(null, resultados);
    }

    /** Corre una política en un sistema aparte, ciclo a ciclo y sin hilo de reloj, y lo apaga al terminar. */
    private static ResultadoPolitica simular(DatosSistemaArchivos sistema, DatosProcesos datos, long maxCiclos,
            Numeros numeros) {
        CargaSimulacion propia = new CargaSimulacion();
        propia.usarSistemaArchivos(sistema);
        propia.usarProcesos(datos);
        Cluster cluster = propia.armar(new LogEventos(EVENTOS_POR_CORRIDA)).cluster();
        try {
            cluster.cargarProcesosArchivo(datos.procesos());
            ListaEnlazada<PCB> creados = cluster.todosLosProcesos();
            numeros.anotar(creados);
            PlanificadorDisco disco = cluster.getComputador(0).getPlanificadorDisco();
            ListaEnlazada<Atencion> orden = new ListaEnlazada<>();
            long vistas = 0;
            long ciclos = 0;
            while (ciclos < maxCiclos && !(cluster.todosTerminados() && disco.estaInactivo())) {
                cluster.getReloj().ejecutarCiclos(1);
                ciclos++;
                if (disco.getAtendidas() > vistas) {
                    vistas = disco.getAtendidas();
                    SolicitudDisco hecha = disco.getUltimaAtendida();
                    orden.agregarFinal(new Atencion(hecha.destino(), hecha.operacion(), nombreDe(creados, hecha.idProceso())));
                }
            }
            int vencidos = 0;
            int rechazados = 0;
            Nodo<PCB> nodo = creados.getCabeza();
            while (nodo != null) {
                if (nodo.getDato().isDeadlineVencido()) {
                    vencidos++;
                } else if (nodo.getDato().getMotivoRechazo() != null) {
                    rechazados++;
                }
                nodo = nodo.getSiguiente();
            }
            Atencion[] atenciones = new Atencion[orden.tamano()];
            Nodo<Atencion> atencion = orden.getCabeza();
            for (int i = 0; i < atenciones.length; i++) {
                atenciones[i] = atencion.getDato();
                atencion = atencion.getSiguiente();
            }
            boolean completo = cluster.todosTerminados() && disco.estaInactivo();
            return new ResultadoPolitica(datos.politicaDisco(), atenciones, cluster.getMetricasDisco(), creados.tamano(),
                    vencidos, rechazados, completo, cluster.getReloj().getCiclo());
        } finally {
            cluster.apagar();
        }
    }

    /** Nombre del proceso con ese número. */
    private static String nombreDe(ListaEnlazada<PCB> procesos, int id) {
        Nodo<PCB> nodo = procesos.getCabeza();
        while (nodo != null && nodo.getDato().getId() != id) {
            nodo = nodo.getSiguiente();
        }
        return nodo == null ? "?" : nodo.getDato().getNombre();
    }

    /** Deja constancia en la simulación abierta del salto que habrá en su numeración. */
    private static void anotarNumeros(Cluster abierta, Numeros numeros) {
        if (abierta == null || numeros.cantidad == 0) {
            return;
        }
        String aviso = numeros.cantidad == numeros.mayor - numeros.menor + 1 ? ""
                : " (no todos: la simulación abierta creó procesos mientras tanto)";
        abierta.getLog().registrar(abierta.getReloj().getCiclo(), "El comparador de políticas usó los números de proceso P"
                + numeros.menor + " a P" + numeros.mayor + aviso);
    }

    /** Rango de números de proceso consumidos por las simulaciones. */
    private static final class Numeros {

        private int menor = Integer.MAX_VALUE;
        private int mayor = 0;
        private int cantidad;

        /** Suma al rango los números de estos procesos. */
        private void anotar(ListaEnlazada<PCB> procesos) {
            Nodo<PCB> nodo = procesos.getCabeza();
            while (nodo != null) {
                menor = Math.min(menor, nodo.getDato().getId());
                mayor = Math.max(mayor, nodo.getDato().getId());
                cantidad++;
                nodo = nodo.getSiguiente();
            }
        }
    }
}
