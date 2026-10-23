package avilaos.pruebas;

import avilaos.enums.EstadoProceso;
import avilaos.enums.MotivoBloqueo;
import avilaos.enums.TipoInstruccion;
import avilaos.enums.TipoInterrupcion;
import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.CPU;
import avilaos.hardware.ComponenteReloj;
import avilaos.hardware.ContextoCPU;
import avilaos.hardware.DMA;
import avilaos.hardware.Interrupcion;
import avilaos.hardware.LineaInterrupciones;
import avilaos.hardware.RAM;
import avilaos.hardware.Reloj;
import avilaos.hardware.SistemaOperativo;
import avilaos.hardware.TarjetaRed;
import avilaos.log.Debug;
import avilaos.so.DatosProceso;
import avilaos.so.FabricaProcesos;
import avilaos.so.GeneradorIds;
import avilaos.so.PCB;
import avilaos.so.programas.Instruccion;
import avilaos.so.programas.ProgramaIOBound;
import avilaos.vista.ReporteTerminal;
import java.util.concurrent.Semaphore;

/** Pruebas de terminal de la etapa 3: reloj, hilos por componente, CPU, RAM, DMA y red. */
public final class PruebasPaso3 {

    private static final String ORIGEN = "Pruebas";
    private static Resultados r;

    private PruebasPaso3() {
    }

    /** Ejecuta todas las pruebas de la etapa 3 e imprime su resumen. */
    public static void ejecutar() {
        GeneradorIds.reiniciar();
        r = new Resultados(3, "Etapa 3: hardware (reloj, CPU, RAM, DMA, red)");
        probarFasesYHilos();
        probarRelojEnSuHilo();
        probarErroresDeReloj();
        probarRAM();
        probarDispositivos();
        probarCPUUnaAccionPorCiclo();
        probarComputadorCompleto();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Cada componente corre en su hilo, hace un tick por ciclo y la fase E/S termina antes que la fase CPU. */
    private static void probarFasesYHilos() {
        r.grupo("Fases del reloj e hilos por componente (barrera con Semaphore)");
        Registro registro = new Registro();
        Reloj reloj = new Reloj(0);
        int faseES = reloj.agregarFase("E/S");
        int faseCPU = reloj.agregarFase("CPU");
        reloj.agregarComponente(faseES, new Anotador("D1", registro));
        reloj.agregarComponente(faseES, new Anotador("D2", registro));
        reloj.agregarComponente(faseCPU, new Anotador("C1", registro));
        reloj.agregarComponente(faseCPU, new Anotador("C2", registro));
        int[] cierres = new int[1];
        reloj.agregarObservador(ciclo -> cierres[0]++);
        final int ciclos = 5;
        reloj.ejecutarCiclos(ciclos);
        reloj.apagar();

        String[] entradas = registro.copiar();
        int[][] ticks = new int[ciclos + 1][4];
        boolean[] faseCPUEmpezada = new boolean[ciclos + 1];
        boolean ordenCorrecto = true;
        String[] hilos = new String[4];
        for (String entrada : entradas) {
            String[] partes = entrada.split("\\|");
            int c = Integer.parseInt(partes[0]);
            int indice = indiceAnotador(partes[1]);
            ticks[c][indice]++;
            hilos[indice] = partes[2];
            if (partes[1].startsWith("C")) {
                faseCPUEmpezada[c] = true;
            } else if (faseCPUEmpezada[c]) {
                ordenCorrecto = false;
            }
        }
        boolean unTickPorCiclo = entradas.length == ciclos * 4;
        for (int c = 1; c <= ciclos; c++) {
            for (int i = 0; i < 4; i++) {
                unTickPorCiclo &= ticks[c][i] == 1;
            }
        }
        boolean hilosDistintos = true;
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                hilosDistintos &= hilos[i] != null && !hilos[i].equals(hilos[j]);
            }
            hilosDistintos &= hilos[i] != null && !hilos[i].equals(Thread.currentThread().getName());
        }
        r.verificar("cada componente hace exactamente 1 tick por ciclo", unTickPorCiclo, entradas.length + " ticks");
        r.verificar("la fase E/S termina antes de empezar la fase CPU", ordenCorrecto, String.join(" ", entradas));
        r.verificar("cada componente corre en su propio hilo", hilosDistintos, hilos[0] + ", " + hilos[1] + ", " + hilos[2] + ", " + hilos[3]);
        r.verificar("el observador recibe el cierre de cada ciclo", cierres[0] == ciclos && reloj.getCiclo() == ciclos,
                cierres[0] + " cierres, ciclo " + reloj.getCiclo());
    }

    /** El reloj avanza solo en su hilo, se pausa, se reanuda y se detiene. */
    private static void probarRelojEnSuHilo() {
        r.grupo("Reloj en su propio hilo (iniciar, pausar, reanudar, detener)");
        Reloj reloj = new Reloj(2);
        int fase = reloj.agregarFase("CPU");
        reloj.agregarComponente(fase, new Anotador("C1", new Registro()));
        boolean dl = Debug.isActivo();
        Debug.setActivo(false);
        reloj.iniciar();
        boolean avanzo = esperarCiclo(reloj, 10, 3000);
        reloj.pausar();
        dormir(30);
        long enPausa = reloj.getCiclo();
        dormir(60);
        boolean quieto = reloj.getCiclo() == enPausa;
        reloj.reanudar();
        boolean siguio = esperarCiclo(reloj, enPausa + 5, 3000);
        reloj.detener();
        long alDetener = reloj.getCiclo();
        dormir(30);
        boolean detenido = reloj.getCiclo() == alDetener && !reloj.isEjecutando();
        reloj.apagar();
        Debug.setActivo(dl);
        r.verificar("avanza solo con duración de 2 ms por ciclo", avanzo, "ciclo " + reloj.getCiclo());
        r.verificar("en pausa no avanza", quieto, "pausa en " + enPausa + ", luego " + reloj.getCiclo());
        r.verificar("al reanudar sigue avanzando", siguio, "ciclo " + reloj.getCiclo());
        r.verificar("al detener no avanza más", detenido, "ciclo " + reloj.getCiclo());
    }

    /** Un componente que falla no traba la barrera; valores inválidos se rechazan. */
    private static void probarErroresDeReloj() {
        r.grupo("Errores controlados del reloj");
        Reloj reloj = new Reloj(0);
        int fase = reloj.agregarFase("CPU");
        reloj.agregarComponente(fase, new ComponenteReloj() {
            @Override
            public void tick(long ciclo) {
                throw new IllegalStateException("fallo simulado");
            }

            @Override
            public String getNombre() {
                return "Defectuoso";
            }
        });
        Debug.info(ORIGEN, "las 3 líneas ERROR siguientes son provocadas a propósito");
        boolean termino = reloj.ejecutarCiclos(1);
        reloj.apagar();
        r.verificar("un componente que falla no bloquea el ciclo", termino && reloj.getCiclo() == 1, "ciclo " + reloj.getCiclo());
        r.verificar("rechaza duración negativa", !reloj.setDuracionCicloMs(-5) && reloj.getDuracionCicloMs() == 0,
                String.valueOf(reloj.getDuracionCicloMs()));
        r.verificar("rechaza fase inexistente", !reloj.agregarComponente(7, new Anotador("X", new Registro())), "");
    }

    /** Reservar, liberar, sin espacio, doble reserva y concurrencia. */
    private static void probarRAM() {
        r.grupo("RAM (reservar, liberar, límites e integridad)");
        RAM ram = new RAM("RAM-T", 300);
        r.verificar("reservar 100", ram.reservar("P1-H1", 100) && ram.getUsada() == 100, String.valueOf(ram.getUsada()));
        r.verificar("reservar hasta llenar", ram.reservar("P2-H1", 200) && ram.getLibre() == 0, String.valueOf(ram.getLibre()));
        r.verificar("sin espacio devuelve false (no es error)", !ram.reservar("P3-H1", 1) && ram.getUsada() == 300, "");
        r.verificar("liberar devuelve la cantidad", ram.liberar("P1-H1") == 100 && ram.getUsada() == 200, String.valueOf(ram.getUsada()));
        Debug.info(ORIGEN, "las 2 líneas ERROR siguientes son provocadas a propósito");
        r.verificar("rechaza doble reserva del mismo dueño", !ram.reservar("P2-H1", 10), "");
        r.verificar("rechaza liberar sin reserva", ram.liberar("P9-H1") == 0, "");
        r.verificar("integridad (suma = usada)", ram.verificarIntegridad(), "");

        RAM compartida = new RAM("RAM-C", 500);
        boolean dl = Debug.isActivo();
        Debug.setActivo(false);
        Thread[] hilos = new Thread[4];
        for (int h = 0; h < hilos.length; h++) {
            final int fila = h;
            hilos[h] = new Thread(() -> {
                for (int i = 0; i < 250; i++) {
                    compartida.reservar("h" + fila + "-" + i, 1);
                }
            });
            hilos[h].start();
        }
        for (Thread t : hilos) {
            unir(t);
        }
        Debug.setActivo(dl);
        r.verificar("4 hilos piden 1000 unidades de 500: entran exactamente 500", compartida.getUsada() == 500
                && compartida.verificarIntegridad(), String.valueOf(compartida.getUsada()));
    }

    /** DMA y red cuentan los ciclos y generan su interrupción al terminar. */
    private static void probarDispositivos() {
        r.grupo("DMA y tarjeta de red (cuenta de ciclos e interrupciones)");
        LineaInterrupciones linea = new LineaInterrupciones("IRQ-T");
        DMA dma = new DMA("DMA-T", linea);
        TarjetaRed red = new TarjetaRed("Red-T", linea);
        dma.solicitar(7, "P7-H1", 3, "E/S");
        red.solicitar(8, "P8-H1", 2, "acceso remoto a B1");
        dma.tick(1);
        red.tick(1);
        r.verificar("a mitad no hay interrupciones", linea.cantidadPendientes() == 0, String.valueOf(linea.cantidadPendientes()));
        dma.tick(2);
        red.tick(2);
        ListaEnlazada<Interrupcion> tras2 = linea.tomarTodas();
        r.verificar("la red interrumpe a los 2 ciclos", tras2.tamano() == 1 && tras2.primero().tipo() == TipoInterrupcion.FIN_RED
                && tras2.primero().idProceso() == 8, tras2.toString());
        dma.tick(3);
        ListaEnlazada<Interrupcion> tras3 = linea.tomarTodas();
        r.verificar("el DMA interrumpe a los 3 ciclos", tras3.tamano() == 1 && tras3.primero().tipo() == TipoInterrupcion.FIN_ES
                && dma.cantidadPendientes() == 0 && dma.getCompletadas() == 1, tras3.toString());
        Debug.info(ORIGEN, "la línea ERROR siguiente es provocada a propósito");
        r.verificar("rechaza duración 0", !dma.solicitar(9, "P9-H1", 0, "E/S"), "");
    }

    /** La CPU exige exactamente una acción del SO por ciclo. */
    private static void probarCPUUnaAccionPorCiclo() {
        r.grupo("CPU: exactamente una acción por ciclo");
        LineaInterrupciones linea = new LineaInterrupciones("IRQ-T");
        CPU cpu = new CPU("CPU-T", linea);
        cpu.tick(1);
        r.verificar("sin SO conectado queda ociosa", cpu.isOciosa() && cpu.getCiclosOciosos() == 1, "");
        Debug.info(ORIGEN, "las 2 líneas ERROR siguientes son provocadas a propósito");
        cpu.conectarSistemaOperativo((c, interrupciones, ciclo) -> {
        });
        cpu.tick(2);
        r.verificar("SO sin acción: ERROR y cuenta como ociosa", cpu.getCiclosOciosos() == 2, String.valueOf(cpu.getCiclosOciosos()));
        cpu.conectarSistemaOperativo((c, interrupciones, ciclo) -> {
            c.ejecutarCodigoSO("acción 1");
            c.ejecutarCodigoSO("acción 2");
        });
        cpu.tick(3);
        r.verificar("SO con 2 acciones: ERROR", cpu.getCiclosSO() == 2, String.valueOf(cpu.getCiclosSO()));
    }

    /** Un computador armado (reloj + DMA + CPU + RAM) ejecuta 3 procesos reales con un SO mínimo de prueba. */
    private static void probarComputadorCompleto() {
        r.grupo("Computador de prueba: 3 procesos sobre el hardware (30 ciclos)");
        LineaInterrupciones linea = new LineaInterrupciones("IRQ-PC1");
        RAM ram = new RAM("RAM-PC1", 300);
        DMA dma = new DMA("DMA-PC1", linea);
        CPU cpu = new CPU("CPU-PC1", linea);
        SOPrueba so = new SOPrueba(ram, dma);
        cpu.conectarSistemaOperativo(so);
        Reloj reloj = new Reloj(0);
        reloj.agregarComponente(reloj.agregarFase("E/S"), dma);
        reloj.agregarComponente(reloj.agregarFase("CPU"), cpu);
        String fases = reloj.describirFases();
        Debug.info(ORIGEN, "fases del reloj: " + fases);

        PCB calc = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("calc", 3, 100, 100, 6, 0), 0));
        PCB io = r.registrar(FabricaProcesos.crear(DatosProceso.ioBound("io", 4, 100, 100, 7, 3, 4, 0), 0));
        PCB grande = r.registrar(FabricaProcesos.crear(DatosProceso.cpuBound("grande", 5, 250, 100, 4, 0), 0));
        so.agregar(calc);
        so.agregar(io);
        so.agregar(grande);

        reloj.ejecutarCiclos(30);
        reloj.apagar();

        r.verificar("los 3 procesos terminan", calc.getEstado() == EstadoProceso.TERMINADO
                && io.getEstado() == EstadoProceso.TERMINADO && grande.getEstado() == EstadoProceso.TERMINADO, "");
        r.verificar("'grande' espera en Nuevos hasta que hay RAM (respuesta 24)", grande.getTiempoRespuesta() == 24,
                String.valueOf(grande.getTiempoRespuesta()));
        r.verificar("tiempos de respuesta calc=1, io=8", calc.getTiempoRespuesta() == 1 && io.getTiempoRespuesta() == 8,
                calc.getTiempoRespuesta() + ", " + io.getTiempoRespuesta());
        r.verificar("CPU: 15 usuario, 7 SO, 8 ociosa", cpu.getCiclosUsuario() == 15 && cpu.getCiclosSO() == 7
                && cpu.getCiclosOciosos() == 8, cpu.getCiclosUsuario() + "/" + cpu.getCiclosSO() + "/" + cpu.getCiclosOciosos());
        r.verificar("DMA atendió las 2 E/S de 'io'", dma.getCompletadas() == 2 && dma.cantidadPendientes() == 0, "");
        r.verificar("RAM vacía al final e íntegra", ram.getUsada() == 0 && ram.verificarIntegridad(), String.valueOf(ram.getUsada()));
        r.verificar("registros de la CPU coinciden con el PCB al guardar contexto", so.contextoCoherente, "");

        r.seccion(" Estado del hardware al final del computador de prueba (30 ciclos):");
        r.seccion(ReporteTerminal.lineaCPU(cpu));
        r.seccion(ReporteTerminal.lineaRAM(ram));
        r.seccion(ReporteTerminal.lineaDispositivo(dma));
        r.seccion(" Fases del reloj: " + fases.replace(" -> ", "  ->  "));
    }

    /** SO mínimo de prueba: FCFS, admisión por RAM, despacho de 1 ciclo y E/S por DMA. Lo reemplaza el Kernel en la etapa 4. */
    private static final class SOPrueba implements SistemaOperativo {

        private final RAM ram;
        private final DMA dma;
        private final Cola<PCB> nuevos = new Cola<>();
        private final Cola<PCB> listos = new Cola<>();
        private final ListaEnlazada<PCB> todos = new ListaEnlazada<>();
        private PCB actual;
        private boolean contextoCoherente = true;

        SOPrueba(RAM ram, DMA dma) {
            this.ram = ram;
            this.dma = dma;
        }

        void agregar(PCB pcb) {
            todos.agregarFinal(pcb);
            nuevos.encolar(pcb);
        }

        @Override
        public void ejecutarCiclo(CPU cpu, ListaEnlazada<Interrupcion> interrupciones, long ciclo) {
            Nodo<Interrupcion> interrupcion = interrupciones.getCabeza();
            while (interrupcion != null) {
                PCB pcb = buscar(interrupcion.getDato().idProceso());
                if (pcb != null && pcb.desbloquear(ciclo)) {
                    listos.encolar(pcb);
                }
                interrupcion = interrupcion.getSiguiente();
            }
            admitir();
            if (actual == null) {
                if (listos.estaVacia()) {
                    cpu.quedarOciosa();
                    return;
                }
                actual = listos.desencolar();
                actual.despachar(ciclo);
                cpu.cargarContexto(actual.getId(), actual.getEtiqueta(), actual.getContador(), actual.getMar());
                cpu.ejecutarCodigoSO("despacha " + actual.getEtiqueta());
                return;
            }
            Instruccion instruccion = actual.instruccionActual();
            cpu.ejecutarInstruccion(instruccion.modo(), instruccion.toString());
            actual.avanzarContador();
            if (instruccion.tipo() == TipoInstruccion.SOLICITAR_ES) {
                liberarCPU(cpu);
                int duracion = ((ProgramaIOBound) actual.getPrograma()).getDuracionES();
                actual.bloquear(MotivoBloqueo.ENTRADA_SALIDA, duracion + " ciclos en DMA", ciclo);
                dma.solicitar(actual.getId(), actual.getEtiqueta(), duracion, "E/S");
                actual = null;
            } else if (actual.programaTerminado()) {
                liberarCPU(cpu);
                actual.terminar(ciclo, false);
                ram.liberar(actual.getEtiqueta());
                actual = null;
            }
        }

        private void liberarCPU(CPU cpu) {
            ContextoCPU contexto = cpu.guardarContexto();
            if (contexto == null || contexto.contador() != actual.getContador() || contexto.mar() != actual.getMar()) {
                contextoCoherente = false;
            }
        }

        private void admitir() {
            int cantidad = nuevos.tamano();
            for (int i = 0; i < cantidad; i++) {
                PCB pcb = nuevos.desencolar();
                if (ram.cabe(pcb.getMemoria()) && ram.reservar(pcb.getEtiqueta(), pcb.getMemoria())) {
                    pcb.cambiarEstado(EstadoProceso.LISTO);
                    listos.encolar(pcb);
                } else {
                    nuevos.encolar(pcb);
                }
            }
        }

        private PCB buscar(int id) {
            Nodo<PCB> nodo = todos.getCabeza();
            while (nodo != null) {
                if (nodo.getDato().getId() == id) {
                    return nodo.getDato();
                }
                nodo = nodo.getSiguiente();
            }
            return null;
        }
    }

    /** Componente de prueba que anota ciclo|nombre|hilo en cada tick. */
    private record Anotador(String nombre, Registro registro) implements ComponenteReloj {

        @Override
        public void tick(long ciclo) {
            registro.anotar(ciclo + "|" + nombre + "|" + Thread.currentThread().getName());
        }

        @Override
        public String getNombre() {
            return nombre;
        }
    }

    /** Lista de anotaciones protegida para escribir desde varios hilos. */
    private static final class Registro {

        private final ListaEnlazada<String> entradas = new ListaEnlazada<>();
        private final Semaphore mutex = new Semaphore(1);

        void anotar(String texto) {
            mutex.acquireUninterruptibly();
            try {
                entradas.agregarFinal(texto);
            } finally {
                mutex.release();
            }
        }

        String[] copiar() {
            mutex.acquireUninterruptibly();
            try {
                String[] copia = new String[entradas.tamano()];
                Nodo<String> nodo = entradas.getCabeza();
                for (int i = 0; i < copia.length; i++) {
                    copia[i] = nodo.getDato();
                    nodo = nodo.getSiguiente();
                }
                return copia;
            } finally {
                mutex.release();
            }
        }
    }

    private static int indiceAnotador(String nombre) {
        return switch (nombre) {
            case "D1" -> 0;
            case "D2" -> 1;
            case "C1" -> 2;
            default -> 3;
        };
    }

    private static boolean esperarCiclo(Reloj reloj, long objetivo, long maximoMs) {
        long limite = System.currentTimeMillis() + maximoMs;
        while (reloj.getCiclo() < objetivo && System.currentTimeMillis() < limite) {
            dormir(5);
        }
        return reloj.getCiclo() >= objetivo;
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void unir(Thread hilo) {
        try {
            hilo.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
