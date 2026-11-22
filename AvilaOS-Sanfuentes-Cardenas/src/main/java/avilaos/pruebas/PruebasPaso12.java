package avilaos.pruebas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.DiscoSD;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.disco.PlanificadorDisco;
import avilaos.so.disco.SolicitudDisco;
import avilaos.so.disco.politicas.FabricaPoliticasDisco;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas de terminal de la etapa 12: planificador de disco y sus seis políticas. */
public final class PruebasPaso12 {

    private static final String DISCO = "SD-PC0";
    private static final String PLANIFICADOR = "PlanDisco-PC0";
    private static final int[] CASOS = {95, 180, 34, 119, 11, 123, 62, 64};
    private static final int[] ANEXO = {82, 17, 43, 140, 24, 16, 190};
    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso12() {
    }

    /** Ejecuta todas las pruebas de la etapa 12 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(12, "Etapa 12: planificador de disco (FIFO, SSTF, SCAN, C-SCAN, LOOK, C-LOOK)");
        probarCasosDelEnunciado();
        probarAnexo();
        probarConSwap();
        probarTiempos();
        probarBordes();
        probarLlegadasDuranteElBarrido();
        probarCambioDePolitica();
        probarSolicitudesInvalidas();
        probarEsperaYAvisos();
        probarFabricaEInterfaz();
        probarConRelojYConcurrencia();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** Casos P1 a P4 del enunciado: cabezal en 50, subiendo, disco de 200 bloques. */
    private static void probarCasosDelEnunciado() {
        r.grupo("Casos P1-P4 del enunciado (cabezal 50, sube, 200 bloques)");
        long errores = Debug.getErrores();
        r.verificar("P1 FIFO: 95 > 180 > 34 > 119 > 11 > 123 > 62 > 64",
                "95>180>34>119>11>123>62>64".equals(simular(PoliticaDiscoTipo.FIFO, 0, 50, CASOS, 1).orden), "");
        r.verificar("P2 SSTF: 62 > 64 > 34 > 11 > 95 > 119 > 123 > 180",
                "62>64>34>11>95>119>123>180".equals(simular(PoliticaDiscoTipo.SSTF, 0, 50, CASOS, 1).orden), "");
        Corrida scan = simular(PoliticaDiscoTipo.SCAN, 0, 50, CASOS, 1);
        r.verificar("P3 SCAN: 62 > 64 > 95 > 119 > 123 > 180 > (199) > 34 > 11",
                "62>64>95>119>123>180>34>11".equals(scan.orden) && scan.maximo == 199, scan.orden + " máx " + scan.maximo);
        Corrida cscan = simular(PoliticaDiscoTipo.C_SCAN, 0, 50, CASOS, 1);
        r.verificar("P4 C-SCAN: 62 > 64 > 95 > 119 > 123 > 180 > (salta al 0) > 11 > 34",
                "62>64>95>119>123>180>11>34".equals(cscan.orden) && cscan.saltos == 1, cscan.orden + " saltos " + cscan.saltos);
        r.verificar("los cuatro casos sin ERROR (una acción por ciclo)", Debug.getErrores() == errores, "");
        r.seccion(" P1 FIFO 95>180>34>119>11>123>62>64 · P2 SSTF 62>64>34>11>95>119>123>180");
        r.seccion(" P3 SCAN 62>64>95>119>123>180>(199)>34>11 · P4 C-SCAN 62>64>95>119>123>180>(0)>11>34");
    }

    /** Anexo del enunciado: orden y desplazamiento exactos (cabezal 50, sube, cola 82, 17, 43, 140, 24, 16, 190). */
    private static void probarAnexo() {
        r.grupo("Anexo: orden y desplazamiento exactos");
        verificarAnexo(PoliticaDiscoTipo.FIFO, "82>17>43>140>24>16>190", 518);
        verificarAnexo(PoliticaDiscoTipo.SSTF, "43>24>17>16>82>140>190", 208);
        verificarAnexo(PoliticaDiscoTipo.SCAN, "82>140>190>43>24>17>16", 332);
        verificarAnexo(PoliticaDiscoTipo.C_SCAN, "82>140>190>16>17>24>43", 192);
        verificarAnexo(PoliticaDiscoTipo.LOOK, "82>140>190>43>24>17>16", 314);
        verificarAnexo(PoliticaDiscoTipo.C_LOOK, "82>140>190>16>17>24>43", 167);
        r.seccion(" Anexo: FIFO 518 · SSTF 208 · SCAN 332 · C-SCAN 192 · LOOK 314 · C-LOOK 167 bloques recorridos");
    }

    /** Con la partición de swap el disco mide 300 posiciones: el orden no cambia y SCAN llega al fin físico (299). */
    private static void probarConSwap() {
        r.grupo("Con swap (200 + 100): mismo orden, extremo físico 299");
        r.verificar("P1 FIFO conserva el orden", "95>180>34>119>11>123>62>64".equals(simular(PoliticaDiscoTipo.FIFO, 100, 50, CASOS, 1).orden), "");
        r.verificar("P2 SSTF conserva el orden", "62>64>34>11>95>119>123>180".equals(simular(PoliticaDiscoTipo.SSTF, 100, 50, CASOS, 1).orden), "");
        Corrida scan = simular(PoliticaDiscoTipo.SCAN, 100, 50, CASOS, 1);
        r.verificar("P3 SCAN conserva el orden y llega al 299", "62>64>95>119>123>180>34>11".equals(scan.orden) && scan.maximo == 299,
                scan.orden + " máx " + scan.maximo);
        r.verificar("P4 C-SCAN conserva el orden", "62>64>95>119>123>180>11>34".equals(simular(PoliticaDiscoTipo.C_SCAN, 100, 50, CASOS, 1).orden), "");
        Corrida anexoScan = simular(PoliticaDiscoTipo.SCAN, 100, 50, ANEXO, 1);
        r.verificar("anexo SCAN con swap: (299-50) + (299-16) = 532", anexoScan.desplazamiento == 532, anexoScan.desplazamiento + "");
        Corrida anexoCscan = simular(PoliticaDiscoTipo.C_SCAN, 100, 50, ANEXO, 1);
        r.verificar("anexo C-SCAN con swap: (299-50) + 43 = 292", anexoCscan.desplazamiento == 292, anexoCscan.desplazamiento + "");
        r.verificar("LOOK no depende del tamaño del disco: 314 igual", simular(PoliticaDiscoTipo.LOOK, 100, 50, ANEXO, 1).desplazamiento == 314, "");
    }

    /** Cada ciclo es una sola acción: moverse (hasta la velocidad), leer o escribir un bloque, o saltar. */
    private static void probarTiempos() {
        r.grupo("Tiempo: ciclos de movimiento, de transferencia y de salto");
        Corrida fifo = simular(PoliticaDiscoTipo.FIFO, 0, 50, ANEXO, 1);
        r.verificar("FIFO a velocidad 1: 518 ciclos moviendo + 7 leyendo = 525", fifo.ciclos == 525 && fifo.moviendo == 518
                && fifo.transfiriendo == 7, fifo.ciclos + " = " + fifo.moviendo + " + " + fifo.transfiriendo);
        Corrida rapido = simular(PoliticaDiscoTipo.FIFO, 0, 50, ANEXO, 10);
        r.verificar("FIFO a velocidad 10: 55 ciclos moviendo + 7 = 62 (el desplazamiento no cambia)",
                rapido.ciclos == 62 && rapido.desplazamiento == 518, rapido.ciclos + " ciclos, " + rapido.desplazamiento);
        Corrida cscan = simular(PoliticaDiscoTipo.C_SCAN, 0, 50, ANEXO, 1);
        r.verificar("C-SCAN: 192 moviendo + 1 salto + 7 leyendo = 200 ciclos", cscan.ciclos == 200 && cscan.saltos == 1, cscan.ciclos + "");

        DiscoSD d = new DiscoSD(DISCO, 200, 0, 0, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.FIFO, log);
        p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/a", new int[]{10, 11, 12, 30}, 0);
        int ciclos = hastaInactivo(d, p);
        r.verificar("archivo encadenado 10 > 11 > 12 > 30 desde 0: 10+1, 1+1, 1+1, 18+1 = 34 ciclos", ciclos == 34
                && d.getDesplazamientoTotal() == 30 && d.getCiclosTransfiriendo() == 4, ciclos + " ciclos");
    }

    /** Casos límite: cola vacía, solicitud bajo el cabezal, empates de SSTF y un solo pedido. */
    private static void probarBordes() {
        r.grupo("Bordes: cola vacía, bajo el cabezal, empates");
        long errores = Debug.getErrores();
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.SCAN, log);
        d.tick(1);
        r.verificar("cola vacía: el disco queda ocioso sin ERROR", d.getCiclosOciosos() == 1 && Debug.getErrores() == errores
                && "ocioso".equals(d.getUltimaAccion()), d.getUltimaAccion());
        p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", new int[]{50}, 1);
        int ciclos = hastaInactivo(d, p);
        r.verificar("solicitud justo bajo el cabezal: 1 ciclo (solo lectura), 0 desplazamiento", ciclos == 1
                && d.getDesplazamientoTotal() == 0, ciclos + "");
        r.verificar("SSTF empate 40/60 desde 50: gana la que llegó antes (40)",
                "40>60".equals(simular(PoliticaDiscoTipo.SSTF, 0, 50, new int[]{40, 60}, 1).orden), "");
        r.verificar("SSTF empate al revés: gana 60", "60>40".equals(simular(PoliticaDiscoTipo.SSTF, 0, 50, new int[]{60, 40}, 1).orden), "");
        for (PoliticaDiscoTipo tipo : PoliticaDiscoTipo.values()) {
            r.verificar(tipo.getNombre() + " con una sola solicitud la atiende", "120".equals(simular(tipo, 0, 50, new int[]{120}, 1).orden), "");
        }
        r.verificar("SCAN bajando desde 50: primero los menores, luego toca el 0 y sube",
                "34>11>62".equals(simularBajando(PoliticaDiscoTipo.SCAN, new int[]{62, 11, 34}).orden), "");
        r.verificar("C-SCAN bajando: menores, salto al 199 y sigue bajando",
                "34>11>95>62".equals(simularBajando(PoliticaDiscoTipo.C_SCAN, new int[]{62, 11, 95, 34}).orden), "");
        r.verificar("sin ERROR en los bordes", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    /** SCAN decide en cada ciclo mientras barre hacia el extremo: atiende lo que aparece por delante. */
    private static void probarLlegadasDuranteElBarrido() {
        r.grupo("Solicitudes que llegan mientras el cabezal barre");
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.SCAN, log);
        p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/atras", new int[]{30}, 0);
        for (int c = 1; c <= 10; c++) {
            d.tick(c);
        }
        r.verificar("con solo 30 (detrás), SCAN sigue subiendo hacia el 199", d.getCabezal() == 60, d.getCabezal() + "");
        p.encolar(2, "P2-H1", TipoOperacionES.LEER, "/adelante", new int[]{120}, 10);
        hastaInactivo(d, p);
        r.verificar("la que aparece por delante (120) se atiende en el camino, luego 30", "120>30".equals(orden(p)), orden(p));
        r.verificar("y el barrido sí llegó al 199 antes de volver", d.getDesplazamientoTotal() == (199 - 50) + (199 - 30),
                d.getDesplazamientoTotal() + "");

        DiscoSD d2 = new DiscoSD(DISCO, 200, 0, 50, 1);
        PlanificadorDisco p2 = new PlanificadorDisco(PLANIFICADOR, d2, PoliticaDiscoTipo.SSTF, log);
        p2.encolar(1, "P1-H1", TipoOperacionES.LEER, "/lejos", new int[]{100}, 0);
        for (int c = 1; c <= 5; c++) {
            d2.tick(c);
        }
        p2.encolar(2, "P2-H1", TipoOperacionES.LEER, "/cerca", new int[]{56}, 5);
        hastaInactivo(d2, p2);
        r.verificar("una solicitud ya tomada no se interrumpe (el disco atiende una a la vez): 100 > 56", "100>56".equals(orden(p2)), orden(p2));
    }

    /** La política se cambia en plena ejecución; la solicitud en curso termina igual. */
    private static void probarCambioDePolitica() {
        r.grupo("Cambio de política en plena ejecución");
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.FIFO, log);
        for (int b : CASOS) {
            p.encolar(b, "P" + b + "-H1", TipoOperacionES.LEER, "/f" + b, new int[]{b}, 0);
        }
        int c = 0;
        while (p.getAtendidas() < 1 && c < 1000) {
            d.tick(++c);
        }
        r.verificar("con FIFO atiende primero el 95", "95".equals(orden(p)), orden(p));
        r.verificar("cambia a SSTF", p.cambiarPolitica(PoliticaDiscoTipo.SSTF, c) && p.getPolitica() == PoliticaDiscoTipo.SSTF, "");
        hastaInactivo(d, p);
        r.verificar("el resto sigue SSTF desde el 95: 119 > 123 > 180 > 64 > 62 > 34 > 11",
                "95>119>123>180>64>62>34>11".equals(orden(p)), orden(p));
        r.verificar("atendidas por política: 1 FIFO y 7 SSTF", p.getAtendidasPor(PoliticaDiscoTipo.FIFO) == 1
                && p.getAtendidasPor(PoliticaDiscoTipo.SSTF) == 7 && p.getAtendidas() == 8, "");
        final long cicloCambio = c;
        r.verificar("política nula -> ERROR y se conserva SSTF", conError(() -> !p.cambiarPolitica(null, cicloCambio))
                && p.getPolitica() == PoliticaDiscoTipo.SSTF, "");
    }

    /** Las solicitudes mal formadas se rechazan con ERROR y no entran a la cola. */
    private static void probarSolicitudesInvalidas() {
        r.grupo("Solicitudes inválidas: ERROR y la cola intacta");
        DiscoSD d = new DiscoSD(DISCO, 200, 100, 50, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.FIFO, log);
        r.verificar("bloque fuera del disco", conError(() -> p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", new int[]{300}, 0) == null), "");
        r.verificar("sin bloques", conError(() -> p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", new int[0], 0) == null), "");
        r.verificar("bloques nulos", conError(() -> p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", null, 0) == null), "");
        r.verificar("sin operación", conError(() -> p.encolar(1, "P1-H1", null, "/x", new int[]{5}, 0) == null), "");
        r.verificar("sin proceso", conError(() -> p.encolar(1, " ", TipoOperacionES.LEER, "/x", new int[]{5}, 0) == null), "");
        r.verificar("leer un archivo en el swap", conError(() -> p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", new int[]{250}, 0) == null), "");
        r.verificar("suspender fuera del swap", conError(() -> p.encolar(1, "P1-H1", TipoOperacionES.SUSPENDER, "P1-H1", new int[]{20}, 0) == null), "");
        r.verificar("la cola sigue vacía", p.cantidadEnCola() == 0, p.cantidadEnCola() + "");
        SolicitudDisco suspender = p.encolar(7, "P7-H1", TipoOperacionES.SUSPENDER, "P7-H1", new int[]{200, 201, 202}, 0);
        r.verificar("suspender en el swap sí se acepta (escritura de 3 bloques)", suspender != null && suspender.operacion().isEscritura()
                && suspender.cantidadBloques() == 3 && p.cantidadEnCola() == 1, "");
        int[] original = {5};
        SolicitudDisco copia = p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/x", original, 0);
        original[0] = 99;
        r.verificar("la solicitud guarda su propia copia de los bloques", copia != null && copia.destino() == 5, "");
    }

    /** Espera promedio en la cola y aviso al terminar cada solicitud. */
    private static void probarEsperaYAvisos() {
        r.grupo("Espera en la cola y aviso de solicitud atendida");
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 0, 1);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.FIFO, log);
        StringBuilder avisos = new StringBuilder();
        p.setAlTerminar((s, ciclo) -> avisos.append(avisos.length() > 0 ? "," : "").append(s.etiqueta()).append('@').append(ciclo));
        p.encolar(1, "P1-H1", TipoOperacionES.LEER, "/a", new int[]{0}, 0);
        p.encolar(2, "P2-H1", TipoOperacionES.CREAR, "/b", new int[]{5}, 0);
        hastaInactivo(d, p);
        r.verificar("P1 se atiende en el ciclo 1 y P2 en el 2: espera promedio (1 + 2) / 2 = 1,5", p.getEsperaPromedio() == 1.5,
                p.getEsperaPromedio() + "");
        r.verificar("avisos: P1 al terminar en el ciclo 1, P2 en el 7 (5 moviendo + 1 escribiendo)", "P1-H1@1,P2-H1@7".equals(avisos.toString()),
                avisos.toString());
        r.verificar("historial en orden de atención", "0>5".equals(orden(p)), orden(p));
        r.verificar("el log registra la decisión del disco con la política", ultimoEventoContiene("El disco atiende la solicitud de P2-H1 en el bloque 5 (FIFO)")
                || logContiene("El disco atiende la solicitud de P2-H1 en el bloque 5 (FIFO)"), "");
    }

    /** La interfaz PoliticaDisco permite crear las seis políticas sin tocar el planificador. */
    private static void probarFabricaEInterfaz() {
        r.grupo("Fábrica e interfaz PoliticaDisco");
        boolean tipos = true;
        for (PoliticaDiscoTipo tipo : PoliticaDiscoTipo.values()) {
            tipos &= FabricaPoliticasDisco.crear(tipo).getTipo() == tipo;
        }
        r.verificar("la fábrica crea las 6 políticas con su tipo", tipos, "");
        r.verificar("con null crea FIFO", FabricaPoliticasDisco.crear(null).getTipo() == PoliticaDiscoTipo.FIFO, "");
        r.verificar("desde texto: FIFO, c-scan, C_LOOK, look", PoliticaDiscoTipo.desdeTexto("FIFO") == PoliticaDiscoTipo.FIFO
                && PoliticaDiscoTipo.desdeTexto("c-scan") == PoliticaDiscoTipo.C_SCAN
                && PoliticaDiscoTipo.desdeTexto("C_LOOK") == PoliticaDiscoTipo.C_LOOK
                && PoliticaDiscoTipo.desdeTexto(" look ") == PoliticaDiscoTipo.LOOK, "");
        r.verificar("desde texto inválido o nulo: null", PoliticaDiscoTipo.desdeTexto("ELEVADOR") == null && PoliticaDiscoTipo.desdeTexto(null) == null, "");
        r.verificar("operaciones: suspender escribe en el swap, reactivar lee", TipoOperacionES.SUSPENDER.isEscritura()
                && TipoOperacionES.SUSPENDER.isIntercambio() && !TipoOperacionES.REACTIVAR.isEscritura() && !TipoOperacionES.LEER.isIntercambio(), "");
    }

    /** El disco con su planificador corre en el hilo del reloj mientras otros hilos encolan solicitudes. */
    private static void probarConRelojYConcurrencia() {
        r.grupo("Reloj global y 4 hilos encolando a la vez");
        long errores = Debug.getErrores();
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 3);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, PoliticaDiscoTipo.C_LOOK, log);
        AtomicInteger avisos = new AtomicInteger();
        p.setAlTerminar((s, ciclo) -> avisos.incrementAndGet());
        Reloj reloj = new Reloj(0);
        int fase = reloj.agregarFase("E/S");
        reloj.agregarComponente(fase, d);
        Semaphore fin = new Semaphore(0);
        for (int h = 0; h < 4; h++) {
            final int hilo = h;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        p.encolar(hilo * 100 + i, "P" + hilo + "-" + i, TipoOperacionES.LEER, "/h" + hilo,
                                new int[]{(hilo * 53 + i * 37) % 200}, reloj.getCiclo());
                    }
                } finally {
                    fin.release();
                }
            }, "Prueba-ColaDisco-" + h).start();
        }
        int vueltas = 0;
        boolean encolaron = false;
        while ((!encolaron || !p.estaInactivo()) && vueltas < 2000) {
            reloj.ejecutarCiclos(10);
            encolaron = encolaron || fin.tryAcquire(4);
            vueltas++;
        }
        reloj.apagar();
        r.verificar("se atendieron las 200 solicitudes", p.getAtendidas() == 200 && avisos.get() == 200, p.getAtendidas() + "/" + avisos.get());
        ListaEnlazada<SolicitudDisco> historial = p.copiarHistorial();
        boolean[] vistos = new boolean[201];
        boolean unicos = true;
        Nodo<SolicitudDisco> nodo = historial.getCabeza();
        while (nodo != null) {
            int id = nodo.getDato().id();
            unicos &= id >= 1 && id <= 200 && !vistos[id];
            vistos[id] = true;
            nodo = nodo.getSiguiente();
        }
        r.verificar("cada solicitud se atendió exactamente una vez", unicos && historial.tamano() == 200, "");
        r.verificar("sin ERROR (una acción por ciclo en el hilo del disco)", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Concurrencia: 200 solicitudes de 4 hilos, C-LOOK a velocidad 3, " + reloj.getCiclo() + " ciclos, desplazamiento "
                + d.getDesplazamientoTotal());
    }

    /** Verifica orden y desplazamiento de una política con el ejemplo del anexo. */
    private static void verificarAnexo(PoliticaDiscoTipo tipo, String orden, long desplazamiento) {
        Corrida c = simular(tipo, 0, 50, ANEXO, 1);
        r.verificar(tipo.getNombre() + ": " + orden.replace(">", " > ") + " y " + desplazamiento + " bloques",
                orden.equals(c.orden) && c.desplazamiento == desplazamiento, c.orden + " / " + c.desplazamiento);
    }

    /** Encola una lectura por destino (todas en el ciclo 0) y corre el disco hasta vaciar la cola. */
    private static Corrida simular(PoliticaDiscoTipo tipo, int swap, int cabezal, int[] destinos, int velocidad) {
        DiscoSD d = new DiscoSD(DISCO, 200, swap, cabezal, velocidad);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, tipo, log);
        for (int b : destinos) {
            p.encolar(b, "P" + b + "-H1", TipoOperacionES.LEER, "/f" + b, new int[]{b}, 0);
        }
        return correr(d, p);
    }

    /** Igual que simular, con el cabezal en 50 y bajando. */
    private static Corrida simularBajando(PoliticaDiscoTipo tipo, int[] destinos) {
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 1);
        d.setDireccion(DireccionCabezal.BAJANDO);
        PlanificadorDisco p = new PlanificadorDisco(PLANIFICADOR, d, tipo, log);
        for (int b : destinos) {
            p.encolar(b, "P" + b + "-H1", TipoOperacionES.LEER, "/f" + b, new int[]{b}, 0);
        }
        return correr(d, p);
    }

    /** Hace ticks hasta vaciar la cola y junta los datos de la corrida. */
    private static Corrida correr(DiscoSD d, PlanificadorDisco p) {
        Corrida c = new Corrida();
        int ciclo = 0;
        while (!p.estaInactivo() && ciclo < 100000) {
            d.tick(++ciclo);
            c.maximo = Math.max(c.maximo, d.getCabezal());
        }
        c.ciclos = ciclo;
        c.orden = orden(p);
        c.desplazamiento = d.getDesplazamientoTotal();
        c.moviendo = d.getCiclosMoviendo();
        c.transfiriendo = d.getCiclosTransfiriendo();
        c.saltos = d.getSaltos();
        return c;
    }

    /** Ticks hasta que el planificador queda sin trabajo; devuelve los ciclos usados. */
    private static int hastaInactivo(DiscoSD d, PlanificadorDisco p) {
        int ciclo = 0;
        while (!p.estaInactivo() && ciclo < 100000) {
            d.tick(++ciclo);
        }
        return ciclo;
    }

    /** Destinos atendidos en orden, como texto "62>64>95". */
    private static String orden(PlanificadorDisco p) {
        StringBuilder sb = new StringBuilder();
        Nodo<SolicitudDisco> nodo = p.copiarHistorial().getCabeza();
        while (nodo != null) {
            sb.append(sb.length() > 0 ? ">" : "").append(nodo.getDato().destino());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Indica si el último evento del log contiene el texto. */
    private static boolean ultimoEventoContiene(String texto) {
        return log.ultimos(1, "").contains(texto);
    }

    /** Indica si alguno de los últimos eventos del log contiene el texto. */
    private static boolean logContiene(String texto) {
        for (String evento : log.copiarUltimos(20)) {
            if (evento.contains(texto)) {
                return true;
            }
        }
        return false;
    }

    /** Ejecuta la comprobación y exige que haya emitido al menos un ERROR. */
    private static boolean conError(Comprobacion c) {
        long antes = Debug.getErrores();
        boolean resultado = c.cumple();
        return resultado && Debug.getErrores() > antes;
    }

    /** Comprobación que se ejecuta midiendo los ERROR emitidos. */
    private interface Comprobacion {

        boolean cumple();
    }

    /** Datos de una corrida del disco. */
    private static final class Corrida {

        String orden;
        long desplazamiento;
        long moviendo;
        long transfiriendo;
        long saltos;
        int ciclos;
        int maximo;
    }
}
