package avilaos.pruebas;

import avilaos.enums.DireccionCabezal;
import avilaos.enums.EstadoBloque;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Bloque;
import avilaos.hardware.DiscoSD;
import avilaos.hardware.Reloj;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.disco.AsignadorBloques;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas de terminal de la etapa 11: disco SD aislado, asignación encadenada, swap y cabezal. */
public final class PruebasPaso11 {

    private static final String DISCO = "SD-PC0";
    private static final String ASIGNADOR = "Asignador-PC0";
    private static Resultados r;

    private PruebasPaso11() {
    }

    /** Ejecuta todas las pruebas de la etapa 11 e imprime su resumen. */
    public static void ejecutar(LogEventos log) {
        r = new Resultados(11, "Etapa 11: disco SD (bloques, asignación encadenada, swap y cabezal)");
        probarCreacion();
        probarAsignacionEncadenada();
        probarHuecos();
        probarSinEspacio();
        probarInvalidos();
        probarAsignarDesde();
        probarSwap();
        probarCabezal();
        probarCabezalConReloj();
        probarAleatorio();
        probarConcurrencia();
        Debug.setCiclo(0);
        r.cerrar();
    }

    /** El disco nace con la zona de archivos libre, la partición de swap al final y valores corregidos si son inválidos. */
    private static void probarCreacion() {
        r.grupo("Creación del disco y partición de intercambio");
        DiscoSD d = new DiscoSD(DISCO, 200, 100, 50, 1);
        r.verificar("200 bloques de archivos + 100 de swap = 300 posiciones", d.getBloquesArchivos() == 200
                && d.getBloquesSwap() == 100 && d.getTotalPosiciones() == 300, d.getTotalPosiciones() + "");
        r.verificar("el swap empieza justo después de los archivos (200)", d.getPrimerBloqueSwap() == 200
                && !d.esSwap(199) && d.esSwap(200) && d.esSwap(299), d.getPrimerBloqueSwap() + "");
        Bloque[] mapa = d.copiarMapa();
        boolean estados = true;
        for (Bloque b : mapa) {
            EstadoBloque esperado = b.numero() < 200 ? EstadoBloque.LIBRE : EstadoBloque.SWAP_LIBRE;
            estados &= b.estado() == esperado && b.dueno() == null && b.esUltimo();
        }
        r.verificar("todos libres: LIBRE en archivos y SWAP_LIBRE en el intercambio", estados, "");
        r.verificar("libres por zona: 200 y 100", d.contarLibres(false) == 200 && d.contarLibres(true) == 100,
                d.contarLibres(false) + "/" + d.contarLibres(true));
        r.verificar("cabezal en 50, subiendo, velocidad 1, sin desplazamiento", d.getCabezal() == 50
                && d.getDireccion() == DireccionCabezal.SUBIENDO && d.getVelocidad() == 1 && d.getDesplazamientoTotal() == 0, "");
        r.verificar("disco recién creado íntegro", d.verificarIntegridad(), "");
        DiscoSD sinSwap = new DiscoSD(DISCO, 200, 0, 0, 1);
        r.verificar("swap 0: sin partición de intercambio", sinSwap.getTotalPosiciones() == 200 && sinSwap.contarLibres(true) == 0, "");

        long antes = Debug.getErrores();
        DiscoSD malo = new DiscoSD(DISCO, 0, -5, 999, 0);
        r.verificar("valores inválidos -> 4 ERROR y valores por defecto (200, 100, cabezal 0, velocidad 1)",
                Debug.getErrores() - antes == 4 && malo.getBloquesArchivos() == DiscoSD.BLOQUES_POR_DEFECTO
                && malo.getBloquesSwap() == DiscoSD.SWAP_POR_DEFECTO && malo.getCabezal() == 0 && malo.getVelocidad() == 1,
                (Debug.getErrores() - antes) + " ERROR");
        r.verificar("más del máximo de bloques -> por defecto", new DiscoSD(DISCO, DiscoSD.MAX_BLOQUES + 1, 0, 0, 1)
                .getBloquesArchivos() == DiscoSD.BLOQUES_POR_DEFECTO, "");
        r.seccion(" Disco: 200 bloques de archivos (0..199) + partición de swap de 100 (200..299)");
    }

    /** Cada archivo es una cadena: cada bloque apunta al siguiente y el último a FIN. */
    private static void probarAsignacionEncadenada() {
        r.grupo("Asignación encadenada (primeros libres desde el 0)");
        DiscoSD d = new DiscoSD(DISCO, 20, 0, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        int f1 = a.asignar("/a.txt", 3);
        int f2 = a.asignar("/b.txt", 2);
        r.verificar("primer archivo en el bloque 0", f1 == 0, f1 + "");
        r.verificar("segundo archivo en el bloque 3", f2 == 3, f2 + "");
        r.verificar("cadena de /a.txt: 0 > 1 > 2", "0>1>2".equals(cadena(a, f1)), cadena(a, f1));
        r.verificar("cadena de /b.txt: 3 > 4", "3>4".equals(cadena(a, f2)), cadena(a, f2));
        r.verificar("el último bloque apunta a FIN", d.leer(2).esUltimo() && d.leer(4).esUltimo(), "");
        r.verificar("cada bloque guarda su dueño", "/a.txt".equals(d.leer(1).dueno()) && "/b.txt".equals(d.leer(4).dueno()), "");
        r.verificar("estado ARCHIVO en los ocupados", d.leer(0).estado() == EstadoBloque.ARCHIVO
                && d.leer(5).estado() == EstadoBloque.LIBRE, "");
        r.verificar("libres: 20 - 5 = 15", d.contarLibres(false) == 15, d.contarLibres(false) + "");
        r.verificar("archivo de 1 bloque: cadena de un solo bloque", "5".equals(cadena(a, a.asignar("/c.txt", 1))), "");
        r.verificar("liberar /a.txt devuelve 3 bloques", a.liberarCadena(f1) == 3, "");
        r.verificar("tras liberar: bloques 0..2 libres sin dueño ni enlace", !d.leer(0).isOcupado() && d.leer(0).dueno() == null
                && d.leer(0).esUltimo() && !d.leer(2).isOcupado(), "");
        r.verificar("las otras cadenas quedan intactas", "3>4".equals(cadena(a, f2)), cadena(a, f2));
        r.verificar("disco íntegro", d.verificarIntegridad(), "");
    }

    /** Con huecos, la cadena salta por los bloques libres: así funciona la asignación encadenada. */
    private static void probarHuecos() {
        r.grupo("Huecos: la cadena se arma con bloques no contiguos");
        DiscoSD d = new DiscoSD(DISCO, 12, 0, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        int x = a.asignar("/x", 3);
        a.asignar("/y", 2);
        int z = a.asignar("/z", 2);
        a.liberarCadena(x);
        a.liberarCadena(z);
        int grande = a.asignar("/grande", 6);
        r.verificar("usa los huecos: 0 > 1 > 2 > 5 > 6 > 7", "0>1>2>5>6>7".equals(cadena(a, grande)), cadena(a, grande));
        r.verificar("/y sigue en 3 > 4", "3>4".equals(cadena(a, 3)), cadena(a, 3));
        r.verificar("liberar un bloque del medio de una cadena se rechaza con ERROR", conError(() -> a.liberarCadena(5) == 0), "");
        r.verificar("la cadena sigue completa tras el rechazo", "0>1>2>5>6>7".equals(cadena(a, grande)), "");
        r.verificar("disco íntegro", d.verificarIntegridad(), "");
    }

    /** Si no hay bloques suficientes no se crea nada (ni a medias). */
    private static void probarSinEspacio() {
        r.grupo("Sin espacio: no se asigna nada a medias");
        DiscoSD d = new DiscoSD(DISCO, 10, 5, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        a.asignar("/ocupa", 7);
        String antes = mapa(d);
        long errores = Debug.getErrores();
        r.verificar("pedir 4 con 3 libres -> -1", a.asignar("/no-cabe", 4) == Bloque.FIN, "");
        r.verificar("el mapa no cambió", antes.equals(mapa(d)), "");
        r.verificar("no es ERROR, solo INFO (es una situación normal)", Debug.getErrores() == errores, "");
        r.verificar("los archivos nunca usan la partición de swap", d.contarLibres(true) == 5, d.contarLibres(true) + "");
        r.verificar("pedir exactamente los 3 libres sí cabe", a.asignar("/justo", 3) == 7, "");
        r.verificar("disco lleno: 0 libres en archivos", d.contarLibres(false) == 0, "");
        r.verificar("pedir más que el disco entero -> -1", a.asignar("/enorme", 11) == Bloque.FIN, "");
        r.verificar("disco íntegro", d.verificarIntegridad(), "");
    }

    /** Las llamadas inválidas se rechazan con ERROR sin tocar el mapa. */
    private static void probarInvalidos() {
        r.grupo("Llamadas inválidas: ERROR y el mapa intacto");
        DiscoSD d = new DiscoSD(DISCO, 10, 4, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        a.asignar("/base", 2);
        String antes = mapa(d);
        r.verificar("cantidad 0", conError(() -> a.asignar("/x", 0) == Bloque.FIN), "");
        r.verificar("cantidad negativa", conError(() -> a.asignar("/x", -3) == Bloque.FIN), "");
        r.verificar("dueño vacío", conError(() -> a.asignar("  ", 1) == Bloque.FIN), "");
        r.verificar("dueño nulo", conError(() -> a.asignarSwap(null, 1) == Bloque.FIN), "");
        r.verificar("liberar un bloque libre", conError(() -> a.liberarCadena(8) == 0), "");
        r.verificar("liberar fuera del disco", conError(() -> a.liberarCadena(500) == 0), "");
        r.verificar("recorrer un bloque libre -> cadena vacía", conError(() -> a.recorrer(9).estaVacia()), "");
        r.verificar("leer fuera del disco -> null", conError(() -> d.leer(-1) == null), "");
        r.verificar("escribir con enlace a sí mismo", conError(() -> !d.escribir(5, "/x", 5)), "");
        r.verificar("escribir con enlace de archivos al swap", conError(() -> !d.escribir(5, "/x", 11)), "");
        r.verificar("escribir sin dueño", conError(() -> !d.escribir(5, "", Bloque.FIN)), "");
        r.verificar("el mapa no cambió", antes.equals(mapa(d)), "");
    }

    /** Carga desde JSON: el archivo empieza en un bloque dado y sigue por los libres siguientes. */
    private static void probarAsignarDesde() {
        r.grupo("Asignación desde un bloque inicial (carga desde JSON)");
        DiscoSD d = new DiscoSD(DISCO, 200, 100, 50, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        int[] iniciales = {11, 34, 62, 64, 95, 119, 123, 180};
        boolean todos = true;
        for (int b : iniciales) {
            todos &= a.asignarDesde("/f" + b, 1, b) == b;
        }
        r.verificar("los 8 archivos del ejemplo quedan en 11, 34, 62, 64, 95, 119, 123 y 180", todos, "");
        int g = a.asignarDesde("/grande", 3, 12);
        r.verificar("empieza en 12 y sigue por los libres: 12 > 13 > 14", "12>13>14".equals(cadena(a, g)), cadena(a, g));
        int s = a.asignarDesde("/salta", 3, 63);
        r.verificar("salta el 64 ocupado: 63 > 65 > 66", "63>65>66".equals(cadena(a, s)), cadena(a, s));
        int v = a.asignarDesde("/vuelta", 3, 198);
        r.verificar("al llegar al final da la vuelta: 198 > 199 > 0", "198>199>0".equals(cadena(a, v)), cadena(a, v));
        r.verificar("bloque inicial ocupado -> -1", a.asignarDesde("/choca", 1, 34) == Bloque.FIN, "");
        r.verificar("bloque inicial en el swap -> ERROR", conError(() -> a.asignarDesde("/swap", 1, 250) == Bloque.FIN), "");
        r.verificar("bloque inicial fuera del disco -> ERROR", conError(() -> a.asignarDesde("/fuera", 1, 400) == Bloque.FIN), "");
        r.verificar("disco íntegro", d.verificarIntegridad(), "");
    }

    /** Los procesos suspendidos ocupan la partición de intercambio, tantos bloques como su memoria. */
    private static void probarSwap() {
        r.grupo("Área de intercambio (swap)");
        DiscoSD d = new DiscoSD(DISCO, 200, 100, 50, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        int p7 = a.asignarSwap("P7-H1", 16);
        r.verificar("el primer suspendido entra al inicio del swap (200)", p7 == 200, p7 + "");
        r.verificar("16 bloques encadenados 200..215", a.recorrer(p7).tamano() == 16 && d.leer(215).esUltimo()
                && d.leer(200).siguiente() == 201, "");
        r.verificar("estado SWAP_OCUPADO y dueño P7-H1", d.leer(207).estado() == EstadoBloque.SWAP_OCUPADO
                && "P7-H1".equals(d.leer(207).dueno()), "");
        r.verificar("la zona de archivos no se toca", d.contarLibres(false) == 200, "");
        int p9 = a.asignarSwap("P9-H1", 84);
        r.verificar("swap lleno exactamente (16 + 84 = 100)", p9 == 216 && d.contarLibres(true) == 0, p9 + "");
        r.verificar("swap lleno: un suspendido más -> -1", a.asignarSwap("P10-H1", 1) == Bloque.FIN, "");
        r.verificar("reactivar a P7 libera sus 16 bloques", a.liberarCadena(p7) == 16 && d.contarLibres(true) == 16, "");
        r.verificar("los liberados vuelven a SWAP_LIBRE", d.leer(200).estado() == EstadoBloque.SWAP_LIBRE, "");
        r.verificar("disco sin swap: no se puede suspender", a.asignarSwap("P1-H1", 1) != Bloque.FIN
                && new AsignadorBloques(ASIGNADOR, new DiscoSD(DISCO, 50, 0, 0, 1)).asignarSwap("P1-H1", 1) == Bloque.FIN, "");
        r.verificar("disco íntegro", d.verificarIntegridad(), "");
    }

    /** El cabezal recorre como máximo su velocidad por ciclo, cambia de sentido y no sale del disco. */
    private static void probarCabezal() {
        r.grupo("Cabezal: movimiento, dirección, velocidad y saltos");
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 1);
        d.fijarObjetivo(53);
        int ciclos = avanzarHastaObjetivo(d);
        r.verificar("de 50 a 53 a velocidad 1: 3 ciclos", ciclos == 3 && d.getCabezal() == 53, ciclos + " ciclos");
        r.verificar("desplazamiento 3, sentido subiendo", d.getDesplazamientoTotal() == 3
                && d.getDireccion() == DireccionCabezal.SUBIENDO, "");
        d.fijarObjetivo(40);
        ciclos = avanzarHastaObjetivo(d);
        r.verificar("de 53 a 40: 13 ciclos, sentido bajando", ciclos == 13 && d.getDireccion() == DireccionCabezal.BAJANDO, "");
        r.verificar("desplazamiento acumulado 3 + 13 = 16", d.getDesplazamientoTotal() == 16, d.getDesplazamientoTotal() + "");
        d.setVelocidad(7);
        d.fijarObjetivo(60);
        ciclos = avanzarHastaObjetivo(d);
        r.verificar("velocidad 7, de 40 a 60 (20): 3 ciclos y frena justo en 60", ciclos == 3 && d.getCabezal() == 60, ciclos + "");
        r.verificar("desplazamiento 16 + 20 = 36 (no cuenta de más al frenar)", d.getDesplazamientoTotal() == 36, "");
        d.tick(1);
        r.verificar("sin objetivo el cabezal no se mueve", d.getCabezal() == 60 && d.enObjetivo(), "");
        r.verificar("salto a 0: no suma desplazamiento", d.saltarA(0) && d.getCabezal() == 0
                && d.getDesplazamientoTotal() == 36 && d.getSaltos() == 1, "");
        r.verificar("objetivo fuera del disco -> ERROR, no se mueve", conError(() -> !d.fijarObjetivo(200)) && d.getCabezal() == 0, "");
        r.verificar("velocidad 0 -> ERROR, conserva la anterior", conError(() -> !d.setVelocidad(0)) && d.getVelocidad() == 7, "");
        r.verificar("salto fuera del disco -> ERROR", conError(() -> !d.saltarA(-1)), "");
        d.setDireccion(DireccionCabezal.BAJANDO);
        r.verificar("se puede fijar el sentido (para SCAN)", d.getDireccion() == DireccionCabezal.BAJANDO, "");
        DiscoSD conSwap = new DiscoSD(DISCO, 200, 100, 190, 1);
        conSwap.fijarObjetivo(299);
        r.verificar("con swap, el cabezal llega al fin físico (299)", avanzarHastaObjetivo(conSwap) == 109 && conSwap.getCabezal() == 299, "");
    }

    /** El disco es un componente del reloj: corre en su hilo y avanza exactamente un ciclo por tick. */
    private static void probarCabezalConReloj() {
        r.grupo("El disco en el reloj global (hilo propio)");
        DiscoSD d = new DiscoSD(DISCO, 200, 0, 50, 2);
        Reloj reloj = new Reloj(0);
        int fase = reloj.agregarFase("E/S");
        r.verificar("el reloj acepta al disco como componente", reloj.agregarComponente(fase, d), "");
        d.fijarObjetivo(60);
        reloj.ejecutarCiclos(3);
        r.verificar("3 ciclos a velocidad 2: cabezal en 56", d.getCabezal() == 56, d.getCabezal() + "");
        reloj.ejecutarCiclos(10);
        r.verificar("llega a 60 y se detiene", d.getCabezal() == 60 && d.getCiclosMoviendo() == 5, d.getCiclosMoviendo() + " ciclos moviendo");
        reloj.apagar();
    }

    /** Miles de operaciones al azar contra un modelo simple: los conteos y la integridad deben cuadrar siempre. */
    private static void probarAleatorio() {
        r.grupo("Operaciones aleatorias contra un modelo");
        Azar azar = new Azar(11);
        DiscoSD d = new DiscoSD(DISCO, 150, 60, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        ListaEnlazada<int[]> vivos = new ListaEnlazada<>();
        int usadosArchivo = 0;
        int usadosSwap = 0;
        boolean consistente = true;
        long errores = Debug.getErrores();
        for (int i = 0; i < 3000; i++) {
            boolean swap = azar.entre(0, 3) == 0;
            if (vivos.estaVacia() || azar.entre(0, 1) == 0) {
                int cantidad = azar.entre(1, 12);
                int primero = swap ? a.asignarSwap("S" + i, cantidad) : a.asignar("A" + i, cantidad);
                int libres = swap ? 60 - usadosSwap : 150 - usadosArchivo;
                if ((primero != Bloque.FIN) != (cantidad <= libres)) {
                    consistente = false;
                }
                if (primero != Bloque.FIN) {
                    vivos.agregarFinal(new int[]{primero, cantidad, swap ? 1 : 0});
                    if (swap) {
                        usadosSwap += cantidad;
                    } else {
                        usadosArchivo += cantidad;
                    }
                }
            } else {
                int[] elegido = vivos.eliminarEn(azar.entre(0, vivos.tamano() - 1));
                consistente &= a.liberarCadena(elegido[0]) == elegido[1];
                if (elegido[2] == 1) {
                    usadosSwap -= elegido[1];
                } else {
                    usadosArchivo -= elegido[1];
                }
            }
            if (i % 250 == 0) {
                consistente &= d.verificarIntegridad();
            }
        }
        r.verificar("3000 operaciones: asignaciones y rechazos coinciden con el modelo", consistente, "");
        r.verificar("libres finales cuadran con el modelo", d.contarLibres(false) == 150 - usadosArchivo
                && d.contarLibres(true) == 60 - usadosSwap, d.contarLibres(false) + "/" + d.contarLibres(true));
        r.verificar("disco íntegro al final y sin ERROR", d.verificarIntegridad() && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + " ERROR");
        r.seccion(" Aleatorio: 3000 operaciones, " + vivos.tamano() + " cadenas vivas, " + (150 - usadosArchivo)
                + " libres en archivos y " + (60 - usadosSwap) + " en swap");
    }

    /** Varios hilos asignan y liberan a la vez: los Semaphore deben mantener el mapa íntegro. */
    private static void probarConcurrencia() {
        r.grupo("Concurrencia: 4 hilos asignando y liberando a la vez");
        DiscoSD d = new DiscoSD(DISCO, 300, 100, 0, 1);
        AsignadorBloques a = new AsignadorBloques(ASIGNADOR, d);
        AtomicInteger excepciones = new AtomicInteger();
        AtomicInteger cruces = new AtomicInteger();
        Semaphore fin = new Semaphore(0);
        long errores = Debug.getErrores();
        for (int h = 0; h < 4; h++) {
            final int hilo = h;
            new Thread(() -> {
                try {
                    Azar azar = new Azar(100 + hilo);
                    ListaEnlazada<Integer> propios = new ListaEnlazada<>();
                    for (int i = 0; i < 400; i++) {
                        if (propios.estaVacia() || azar.entre(0, 1) == 0) {
                            boolean swap = azar.entre(0, 4) == 0;
                            String dueno = "H" + hilo + "-" + i;
                            int primero = swap ? a.asignarSwap(dueno, azar.entre(1, 5)) : a.asignar(dueno, azar.entre(1, 8));
                            if (primero != Bloque.FIN) {
                                if (!dueno.equals(d.leer(primero).dueno())) {
                                    cruces.incrementAndGet();
                                }
                                propios.agregarFinal(primero);
                            }
                        } else {
                            a.liberarCadena(propios.eliminarEn(azar.entre(0, propios.tamano() - 1)));
                        }
                    }
                    Nodo<Integer> nodo = propios.getCabeza();
                    while (nodo != null) {
                        a.liberarCadena(nodo.getDato());
                        nodo = nodo.getSiguiente();
                    }
                } catch (RuntimeException e) {
                    excepciones.incrementAndGet();
                } finally {
                    fin.release();
                }
            }, "Prueba-Disco-" + h).start();
        }
        fin.acquireUninterruptibly(4);
        r.verificar("sin excepciones en los hilos", excepciones.get() == 0, excepciones.get() + "");
        r.verificar("ningún hilo recibió bloques de otro", cruces.get() == 0, cruces.get() + "");
        r.verificar("al final todo vuelve a estar libre (300 + 100)", d.contarLibres(false) == 300 && d.contarLibres(true) == 100,
                d.contarLibres(false) + "/" + d.contarLibres(true));
        r.verificar("disco íntegro y sin ERROR", d.verificarIntegridad() && Debug.getErrores() == errores,
                (Debug.getErrores() - errores) + " ERROR");
    }

    /** Hace ticks hasta que el cabezal llega a su objetivo; devuelve los ciclos usados (tope 10000). */
    private static int avanzarHastaObjetivo(DiscoSD d) {
        int ciclos = 0;
        while (!d.enObjetivo() && ciclos < 10000) {
            d.tick(++ciclos);
        }
        return ciclos;
    }

    /** Cadena de bloques como texto "0>1>2". */
    private static String cadena(AsignadorBloques a, int primero) {
        StringBuilder sb = new StringBuilder();
        Nodo<Integer> nodo = a.recorrer(primero).getCabeza();
        while (nodo != null) {
            sb.append(sb.length() > 0 ? ">" : "").append(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    /** Mapa completo como texto, para comparar antes y después. */
    private static String mapa(DiscoSD d) {
        StringBuilder sb = new StringBuilder();
        for (Bloque b : d.copiarMapa()) {
            sb.append(b.estado()).append(':').append(b.dueno()).append(':').append(b.siguiente()).append(';');
        }
        return sb.toString();
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

    /** Generador de números pseudoaleatorios con semilla fija (xorshift), para que cada corrida sea reproducible. */
    private static final class Azar {

        private long estado;

        Azar(long semilla) {
            estado = semilla * 0x9E3779B97F4A7C15L + 1;
        }

        /** Entero uniforme entre min y max, ambos incluidos. */
        int entre(int min, int max) {
            estado ^= estado << 13;
            estado ^= estado >>> 7;
            estado ^= estado << 17;
            return min + (int) Math.floorMod(estado, (long) (max - min + 1));
        }
    }
}
